package com.mesosphere.cosmos

import java.net.{MalformedURLException, UnknownHostException}
import java.nio.file._
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

import cats.data.Validated.{Invalid, Valid}
import cats.data.Xor.{Left, Right}
import cats.data._
import cats.std.list._           // allows for traversU in verifySchema
import cats.std.option._
import cats.syntax.apply._       // provides |@|
import cats.syntax.option._
import cats.syntax.traverse._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.model.{PackageRepository, SearchResult}
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe._
import com.netaporter.uri.Uri
import com.twitter.concurrent.AsyncMutex
import com.twitter.io.{Charsets, Files => TwitterFiles}
import com.twitter.util.{Future, Try}
import io.circe.parse._
import io.circe.{Decoder, Json}

import scala.util.matching.Regex

/** Stores packages from the Universe GitHub repository in the local filesystem.
  */
final class UniversePackageCache private (
  override val repository: PackageRepository,
  universeDir: Path
) extends Repository with AutoCloseable {

  import UniversePackageCache._

  // This mutex serializes updates to the local package cache
  private[this] val updateMutex = new AsyncMutex()

  private[this] val lastModified = new AtomicReference(LocalDateTime.MIN)

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    mostRecentBundle().map { case (bundleDir, universeIndex) =>
      val repoDir = repoDirectory(bundleDir)
      val packageDir = getPackagePath(repoDir, universeIndex, packageName, packageVersion)
      readPackageFiles(universeBundle, packageDir)
    }
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles] = {
    mostRecentBundle().map { case (bundleDir, _) =>
      val packageDir = getPackageReleasePath(repoDirectory(bundleDir), packageName, releaseVersion)
      readPackageFiles(universeBundle, packageDir)
    }
  }

  override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
    mostRecentBundle().map { case (bundleDir, universeIndex) =>
      universeIndex.getPackages.getOrElse(packageName, throw PackageNotFound(packageName))
    }
  }

  override def search(queryOpt: Option[String]): Future[List[SearchResult]] = {
    mostRecentBundle().map { case (bundleDir, universeIndex) =>
      UniversePackageCache.search(bundleDir, universeIndex, queryOpt)
    }
  }

  override def close(): Unit = {
    // Note: The order is very important!!

    val symlinkBundlePath = universeDir.resolve(base64(universeBundle))

    // Remember the old bundle path so we can delete it later
    val bundlePath = readSymbolicLink(symlinkBundlePath)

    // Delete the bundle directory
    bundlePath.foreach { p =>
      //TODO: This needs to be more robust to handle potential lingering symlinks
      Files.delete(symlinkBundlePath)  // work around until https://github.com/mesosphere/cosmos/issues/246 is fixed
      // Delete the symbolic link
      TwitterFiles.delete(p.toFile)
    }
  }

  def universeBundle: Uri = repository.uri

  private[this] def mostRecentBundle(): Future[(Path, UniverseIndex)] = {
    synchronizedUpdate().map { bundlePath =>
      val indexFile = repoDirectory(bundlePath).resolve(Paths.get("meta", "index.json"))

      val repoIndex = parseJsonFile(indexFile)
        .toRightXor(new IndexNotFound(universeBundle))
        .flatMap { index =>
          index.as[UniverseIndex].leftMap(_ => PackageFileSchemaMismatch("index.json"))
        }
        .valueOr(err => throw err)

      val repoVersion = repoIndex.version
      if (repoVersion.toString.startsWith("2.")) {
        (bundlePath, repoIndex)
      } else {
        throw new UnsupportedRepositoryVersion(repoVersion)
      }
    }
  }

  private[this] def synchronizedUpdate(): Future[Path] = {
    updateMutex.acquireAndRun {
      // TODO: How often we check should be configurable
      if (lastModified.get().plusMinutes(1).isBefore(LocalDateTime.now())) {
        updateUniverseCache()
          .onSuccess { _ => lastModified.set(LocalDateTime.now()) }
      } else {
        /* We don't need to fetch the latest package information; just return the current
         * information.
         */
        Future(universeDir.resolve(base64(universeBundle)))
      }
    }
  }

  private[this] def updateUniverseCache(): Future[Path] = {
    Future(universeBundle.toURI.toURL.openStream())
      .handle {
        case e @ (_: IllegalArgumentException | _: MalformedURLException | _: UnknownHostException) =>
          throw new InvalidRepositoryUri(repository, e)
      }
      .map { bundleStream =>
        try {
          extractBundle(
            new ZipInputStream(bundleStream),
            universeBundle,
            universeDir
          )
        } finally {
          bundleStream.close()
        }
      }
  }

  private[this] def base64(universeBundle: Uri): String = {
    Base64.getUrlEncoder().encodeToString(
      universeBundle.toString.getBytes(Charsets.Utf8)
    )
  }

  private[this] def extractBundle(
    bundle: ZipInputStream,
    universeBundle: Uri,
    universeDir: Path
  ): Path = {

    val entries = Iterator.continually(Option(bundle.getNextEntry()))
      .takeWhile(_.isDefined)
      .flatten

    val tempDir = Files.createTempDirectory(universeDir, "repository")
    tempDir.toFile.deleteOnExit()

    try {
      // Copy all of the entry to a temporary folder
      entries.foreach { entry =>
        val entryPath = Paths.get(entry.getName)

        // Skip the root directory
        if (entryPath.getNameCount > 1) {
          val tempEntryPath = tempDir
            .resolve(entryPath.subpath(1, entryPath.getNameCount))

          if (entry.isDirectory) {
            Files.createDirectory(tempEntryPath)
          } else {
            Files.copy(bundle, tempEntryPath)
          }
        }
      }

      // Move the symblic to the temp directory to the actual universe directory...
      val bundlePath = universeDir.resolve(base64(universeBundle))
      val newBundlePath = universeDir.resolve(base64(universeBundle) + ".new")

      // Remember the old bundle path so we can delete it later
      val oldBundlePath = readSymbolicLink(bundlePath)

      // Create new symbolic link to the new bundle directory
      Files.createSymbolicLink(newBundlePath, tempDir)

      // Atomic move of the temporary directory
      val path = Files.move(
        newBundlePath,
        bundlePath,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )

      // Delete the old bundle directory
      oldBundlePath.foreach { p => TwitterFiles.delete(p.toFile) }

      path
    } catch {
      case e: Exception =>
      // Only delete directory on failures because we want to keep it around on success.
      TwitterFiles.delete(tempDir.toFile)
      throw e
    }
  }

  private[this] def readSymbolicLink(path: Path): Option[Path] = {
    Try(Files.readSymbolicLink(path))
      .map { path =>
        Some(path)
      }
      .handle {
        case e: NoSuchFileException =>
          // this is okay, we expect the link to not be there the first time
          None
      }
      .get
  }

}

object UniversePackageCache {

  def apply(repository: PackageRepository, dataDir: Path): UniversePackageCache = {
    new UniversePackageCache(repository, dataDir)
  }

  private[cosmos] def search(
    bundleDir: Path,
    universeIndex: UniverseIndex,
    queryOpt: Option[String]
  ): List[SearchResult] = {
    val repoDir = repoDirectory(bundleDir)

    searchIndex(universeIndex.packages, queryOpt)
      .map { indexEntry =>
        val resources = packageResources(repoDir, universeIndex, indexEntry.name)
        searchResult(indexEntry, resources.images)
      }
  }

  private[cosmos] def searchResult(indexEntry: UniverseIndexEntry, images: Option[Images]): SearchResult = {
    SearchResult(
      name = indexEntry.name,
      currentVersion = indexEntry.currentVersion,
      versions = indexEntry.versions,
      description = indexEntry.description,
      framework = indexEntry.framework,
      tags = indexEntry.tags,
      selected = indexEntry.selected,
      images = images
    )
  }

  private[cosmos] def repoDirectory(bundleDir: Path) = bundleDir.resolve("repo")

  private[this] def searchIndex(
    entries: List[UniverseIndexEntry],
    queryOpt: Option[String]
  ): List[UniverseIndexEntry] = {
    queryOpt match {
      case None => entries
      case Some(query) =>
        if (query.contains("*")) {
          val regex = getRegex(query)
          entries.filter(searchRegexInPackageIndex(_, regex))
        } else {
          entries.filter(searchPackageIndex(_, query.toLowerCase()))
        }
    }
  }

  private[this] def searchRegexInPackageIndex(index: UniverseIndexEntry, regex: Regex): Boolean = {
    regex.findFirstIn(index.name).isDefined ||
      regex.findFirstIn(index.description).isDefined ||
      index.tags.exists(regex.findFirstIn(_).isDefined)
  }

  private[this] def searchPackageIndex(index: UniverseIndexEntry, query: String): Boolean= {
    index.name.toLowerCase().contains(query) ||
      index.description.toLowerCase().contains(query) ||
      index.tags.exists(_.toLowerCase().contains(query))
  }

  private[this] def getRegex(query: String): Regex = {
    s"""^${query.replaceAll("\\*", ".*")}$$""".r
  }

  private[cosmos] def packageResources(
    repoDir: Path,
    universeIndex: UniverseIndex,
    packageName: String
  ): Resource = {
    val packageDir = getPackagePath(repoDir, universeIndex, packageName, packageVersion = None)
    parseAndVerify[Resource](packageDir, "resource.json")
      .valueOr(err => throw err)
      .getOrElse(Resource())
  }

  private[this] def parseAndVerify[A : Decoder](
    packageDir: Path,
    fileName: String
  ): Xor[CosmosError, Option[A]] = {
    parseJsonFile(packageDir.resolve(fileName)).traverseU { json =>
      json.as[A].leftMap(_ => PackageFileSchemaMismatch(fileName))
    }
  }

  // return path of specified version, or latest if version not specified
  private def getPackagePath(
    repository: Path,
    universeIndex: UniverseIndex,
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Path = {
    universeIndex.getPackages.get(packageName) match {
      case None => throw PackageNotFound(packageName)
      case Some(packageInfo) =>
        val version = packageVersion.getOrElse(packageInfo.currentVersion)
        packageInfo.versions.get(version) match {
          case None => throw VersionNotFound(packageName, version)
          case Some(revision) =>
            getPackageReleasePath(repository, packageName, revision)
            val packagePath = Paths.get(
              "packages",
              packageName.charAt(0).toUpper.toString,
              packageName,
              revision.toString)
            repository.resolve(packagePath)
        }
    }
  }

  private def getPackageReleasePath(
    repoDir: Path,
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Path = {
    repoDir.resolve(
      Paths.get(
        "packages",
        packageName.charAt(0).toUpper.toString,
        packageName,
        releaseVersion.toString
      )
    )
  }

  private def readPackageFiles(universeBundle: Uri, packageDir: Path): PackageFiles = {

    val packageJson = parseJsonFile(
      packageDir.resolve("package.json")
    ).getOrElse {
      throw PackageFileMissing("package.json")
    }
    val mustache = readFile(
      packageDir.resolve("marathon.json.mustache")
    ).getOrElse {
      throw PackageFileMissing("marathon.json.mustache")
    }

    val packageDefValid = verifySchema[PackageDetails](packageJson, "package.json")
    val resourceDefValid = parseAndVerify[Resource](packageDir, "resource.json").toValidated.toValidatedNel
    val commandJsonValid = parseAndVerify[Command](packageDir, "command.json").toValidated.toValidatedNel
    val configJsonObject = parseJsonFile(packageDir.resolve("config.json"))
      .traverseU { json =>
        json
          .asObject
          .toValidNel[CosmosError](PackageFileSchemaMismatch("config.json"))
      }

    (packageDefValid |@| resourceDefValid |@| commandJsonValid |@| configJsonObject)
      .map { (packageDef, resourceDef, commandJson, configJson) =>
        PackageFiles(
          packageDir.getFileName.toString,
          universeBundle,
          packageDef,
          mustache,
          commandJson,
          configJson,
          resourceDef
        )
      }
      .toXor
      .valueOr(err => throw NelErrors(err))
  }

  private def parseJsonFile(file: Path): Option[Json] = {
    readFile(file).map { content =>
      parse(content) match {
        case Left(err) => throw PackageFileNotJson(file.getFileName.toString, err.message)
        case Right(json) => json
      }
    }
  }

  private[this] def readFile(path: Path): Option[String] = {
    if (path.toFile.exists()) {
      try {
        Some(new String(Files.readAllBytes(path), Charsets.Utf8))
      } catch {
        case e: Throwable =>
          // TODO: This is not the correct error. We return None if the file doesn't exists.
          throw new PackageFileMissing(path.toString, e)
      }
    } else {
      None
    }
  }

  private[this] def verifySchema[A: Decoder](
    json: Json,
    packageFileName: String
  ): ValidatedNel[CosmosError, A] = {
    json
      .as[A]
      .leftMap(_ => PackageFileSchemaMismatch(packageFileName))
      .toValidated
      .toValidatedNel
  }

}
