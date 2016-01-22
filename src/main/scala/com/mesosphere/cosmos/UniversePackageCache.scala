package com.mesosphere.cosmos

import java.nio.file._
import java.util.zip.ZipInputStream

import cats.data.Validated.{Valid, Invalid}
import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.model._
import com.netaporter.uri.Uri
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parse._

/** Stores packages from the Universe GitHub repository in the local filesystem.
  *
  * @param repoDir the local cache of the repository's `repo` directory
  */
final class UniversePackageCache private(repoDir: Path) extends PackageCache {

  import UniversePackageCache._

  override def getPackageFiles(
    packageName: String,
    version: Option[String]
  ): Future[PackageFiles] = {
    getRepoIndex(repoDir) // this will eventually be allIndexes
      .flatMap { repoIndex =>
      getPackagePath(repoDir, repoIndex, packageName, version)
        .flatMap(
          path => readPackageFiles(path, repoIndex.version)
        )
    }
  }

}

object UniversePackageCache {

  /** Create a new package cache.
    *
    * @param universeBundle the location of the package bundle to cache; must be an HTTP URL
    * @param universeDir the directory to cache the bundle files in; assumed to be empty
    * @return The new cache, or an error.
    */
  def apply(universeBundle: Uri, universeDir: Path): Future[UniversePackageCache] = {
    Future(new ZipInputStream(universeBundle.toURI.toURL.openStream()))
      .flatMap { bundleStream =>
        extractBundle(bundleStream, universeDir)
          .ensure(bundleStream.close())
      }
      .map { _ =>
        val rootDir = Files.list(universeDir).findFirst().get()
        new UniversePackageCache(rootDir.resolve(Paths.get("repo")))
      }
  }

  private[this] def extractBundle(bundle: ZipInputStream, cacheDir: Path): Future[Unit] = {
    val entries = Iterator.continually(Option(bundle.getNextEntry()))
      .takeWhile(_.isDefined)
      .flatten

    Future.value {
      entries.foreach { entry =>
        val cachePath = cacheDir.resolve(entry.getName)
        if (entry.isDirectory) {
          Files.createDirectory(cachePath)
        } else {
          Files.copy(bundle, cachePath)
        }
      }
    }
  }

  private def getRepoIndex(repository: Path): Future[UniverseIndex] = {
    val indexFile = repository.resolve(Paths.get("meta", "index.json"))
    parseJsonFile(indexFile)
      .map {
        case None => throw IndexNotFound()
        case Some(index) =>
          index.as[UniverseIndex]
            .getOrElse(throw PackageFileSchemaMismatch("index.json"))
      }
  }

  // return path of specified version, or latest if version not specified
  private def getPackagePath(
    repository: Path,
    universeIndex: UniverseIndex,
    packageName: String,
    packageVersion: Option[String]): Future[Path] = {

    universeIndex.getPackages.get(packageName) match {
      case None => throw PackageNotFound(packageName)
      case Some(packageInfo) =>

        val version = packageVersion.getOrElse(packageInfo.currentVersion)
        packageInfo.versions.get(version) match {
          case None => throw VersionNotFound(packageName, version)
          case Some(revision) =>
            val packagePath = Paths.get("packages",
              packageName.charAt(0).toUpper.toString,
              packageName,
              revision)
            Future.value(repository.resolve(packagePath))
        }
    }
  }

  private def readPackageFiles(
    packageDir: Path,
    version: String
  ): Future[PackageFiles] = {
    Future.join(
        parseJsonFile(packageDir.resolve("command.json")),
        parseJsonFile(packageDir.resolve("config.json")),
        parseJsonFile(packageDir.resolve("package.json")),
        parseJsonFile(packageDir.resolve("resource.json")),
        readFile(packageDir.resolve("marathon.json.mustache"))
      )
      .map { case (commandJsonOpt, configJsonOpt, packageJsonOpt, resourceJsonOpt, mustacheOpt) =>

        val packageJson = packageJsonOpt.getOrElse(throw PackageFileMissing("package.json"))
        val mustache = mustacheOpt.getOrElse(throw PackageFileMissing("marathon.json.mustache"))

        val revision = packageDir.getFileName.toString
        val commandJson = commandJsonOpt.getOrElse(Json.empty)
        val configJson = configJsonOpt.getOrElse(Json.empty)
        val resourceJson = resourceJsonOpt.getOrElse(Json.empty)

        PackageFiles.validate(
          version, revision, commandJson, configJson, mustache, packageJson, resourceJson
        ) match {
          case Invalid(err) => throw NelErrors(err)
          case Valid(valid) => valid
        }
      }
  }

  private def parseJsonFile(file: Path): Future[Option[Json]] = {
    readFile(file).map {
      _.map {
        content =>
          parse(content) match {
            case Left(err) => throw PackageFileNotJson(file.getFileName.toString, err.message)
            case Right(json) => json
          }
      }
    }
  }

  private def readFile(path: Path): Future[Option[String]] = {
    path.toFile.exists() match {
      case false => Future.value(None)
      case true =>
        Future.value(Some(new String(Files.readAllBytes(path), Charsets.Utf8))) // TODO: IO Future Pool
          .rescue { case e: Throwable => Future.exception(PackageFileMissing(path.toString)) }
    }
  }

}
