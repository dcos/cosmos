package com.mesosphere.cosmos

import java.nio.file._
import java.time.LocalDateTime
import java.util.Base64
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
import com.mesosphere.cosmos.circe.Encoders
import com.mesosphere.cosmos.model._
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
  name: String,
  val universeBundle: Uri,
  universeDir: Path
) extends Repository with AutoCloseable {
  // This mutex serializes updates to the local package cache
  private[this] val updateMutex = new AsyncMutex()

  // Protected by updateMutex
  private[this] var lastModified: LocalDateTime = LocalDateTime.MIN
  // Protected by updateMutex
  private[this] var state: RepositoryState = Unused

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    synchronizedUpdate().map { bundleDir =>
      val repoDir = repoDirectory(bundleDir)
      readPackageFiles(
        getPackagePath(
          repoDir,
          repoIndex(repoDir),
          packageName,
          packageVersion
        )
      )
    }
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles] = {
    synchronizedUpdate().map { bundleDir =>
      readPackageFiles(
        getPackageReleasePath(
          repoDirectory(bundleDir),
          packageName,
          releaseVersion
        )
      )
    }
  }

  override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
    synchronizedUpdate().map { bundleDir =>
      repoIndex(repoDirectory(bundleDir)).getPackages.get(packageName).getOrElse(
        throw PackageNotFound(packageName)
      )
    }
  }

  override def search(queryOpt: Option[String]): Future[List[UniverseIndexEntry]] = {
    synchronizedUpdate().map { bundleDir =>
      val packages = repoIndex(repoDirectory(bundleDir)).packages
      val wildcardSymbol = "*"
      queryOpt match {
        case None => packages
        case Some(query) =>
          if (query.contains(wildcardSymbol)) {
            packages.filter(searchRegexInPackageIndex(_, getRegex(query)))
          } else {
            packages.filter(searchPackageIndex(_, query.toLowerCase()))
          }
      }
    }
  }

  override def close(): Unit = {
    // Note: The order is very important!!

    val symlinkBundlePath = universeDir.resolve(base64(universeBundle))

    readSymbolicLink(symlinkBundlePath).foreach { bundlePath =>
      // Delete the symbolic link
      Files.delete(symlinkBundlePath)

      // Delete the bundle directory
      TwitterFiles.delete(bundlePath.toFile)
    }
  }

  private[cosmos] def metadata: RepositoryMetadata = RepositoryMetadata(name, universeBundle, state)

  private[this] def getRegex(query: String): Regex = {
    s"""^${query.replaceAll("\\*", ".*")}$$""".r
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

  private[this] def synchronizedUpdate(): Future[Path] = {
    updateMutex.acquireAndRun {
      // TODO: How often we check should be configurable
      if (lastModified.plusMinutes(1).isBefore(LocalDateTime.now())) {
        val path = updateUniverseCache(universeBundle, universeDir)

        // Update the last modified date
        lastModified = LocalDateTime.now()

        path
      } else {
        /* We don't need to fetch the latest package information; just return the current
         * information.
         */
        Future(universeDir.resolve(base64(universeBundle)))
      }
    }
  }

  private[this] def repoIndex(repoDir: Path): UniverseIndex = {
    val indexFile = repoDir.resolve(Paths.get("meta", "index.json"))

    parseJsonFile(indexFile).map { index =>
      index.as[UniverseIndex].getOrElse(throw PackageFileSchemaMismatch("index.json"))
    } getOrElse {
      throw IndexNotFound(universeBundle)
    }
  }

  private[this] def readPackageFiles(packageDir: Path): PackageFiles = {

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

    validate(
      packageDir.getFileName.toString,
      universeBundle,
      parseJsonFile(packageDir.resolve("command.json")),
      parseJsonFile(packageDir.resolve("config.json")),
      mustache,
      packageJson,
      parseJsonFile(packageDir.resolve("resource.json"))
    ) match {
      case Invalid(err) => throw NelErrors(err)
      case Valid(valid) => valid
    }
  }

  private[this] def updateUniverseCache(
    universeBundle: Uri,
    universeDir: Path
  ): Future[Path] = {
    Future(universeBundle.toURI.toURL.openStream())
      .map { bundleStream =>
        try {
          val path = extractBundle(
            new ZipInputStream(bundleStream),
            universeBundle,
            universeDir
          )

          val repoVersion = repoIndex(repoDirectory(path)).version
          if (!repoVersion.toString.startsWith("2.")) {
            throw new UnsupportedRepositoryVersion(repoVersion)
          }

          state = Healthy
          path
        } finally {
          bundleStream.close()
        }
      }
      .onFailure(t => state = Unhealthy(Encoders.exceptionErrorResponse(t)))
  }

  private[this] def repoDirectory(bundleDir: Path) = bundleDir.resolve("repo")

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

  // return path of specified version, or latest if version not specified
  private[this] def getPackagePath(
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

  private[this] def parseJsonFile(file: Path): Option[Json] = {
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

  private def validate(
    revision: String,
    sourceUri: Uri,
    commandJsonOpt: Option[Json],
    configJsonOption: Option[Json],
    marathonJsonMustache: String,
    packageJsonOpt: Json,
    resourceJsonOpt: Option[Json]
  ): ValidatedNel[CosmosError, PackageFiles] = {
    val packageDefValid = verifySchema[PackageDetails](packageJsonOpt, "package.json")
    val resourceDefValid = verifySchema[Resource](resourceJsonOpt, "resource.json")
    val commandJsonValid = verifySchema[Command](commandJsonOpt, "command.json")
    val configJsonObject = configJsonOption.traverseU {json =>
      json.asObject
        .toValidNel[CosmosError](PackageFileSchemaMismatch("config.json"))
    }

    (packageDefValid |@| resourceDefValid |@| commandJsonValid |@| configJsonObject)
      .map { (packageDef, resourceDef, commandJson, configJson) =>
        PackageFiles(
          revision,
          sourceUri,
          packageDef,
          marathonJsonMustache,
          commandJson,
          configJson,
          resourceDef
        )
      }
  }

  private[this] def verifySchema[A: Decoder](
    json: Json,
    packageFileName: String
  ): ValidatedNel[CosmosError, A] = {
    json
      .as[A]
      .leftMap(_ => errorNel(PackageFileSchemaMismatch(packageFileName)))
      .toValidated
  }

  private[this] def verifySchema[A: Decoder](
    json: Option[Json],
    packageFileName: String
  ): ValidatedNel[CosmosError, Option[A]] = {
    json
      .traverseU(verifySchema[A](_, packageFileName))
  }

  def errorNel(error: CosmosError): NonEmptyList[CosmosError] = NonEmptyList(error)

  private[this] def getPackageReleasePath(
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
}

object UniversePackageCache {

  def apply(name: String, universeBundle: Uri, dataDir: Path): UniversePackageCache = {
    new UniversePackageCache(name, universeBundle, dataDir)
  }
}
