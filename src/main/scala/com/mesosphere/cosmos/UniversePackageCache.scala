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
import io.circe.syntax._
import io.finch._

/** Stores packages from the Universe GitHub repository in the local filesystem.
  *
  * @param repoDir the local cache of the repository's `repo` directory
  */
final class UniversePackageCache private(repoDir: Path, source: Uri) extends PackageCache {

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[String]
  ): Future[PackageFiles] = {
    // This will eventually be allIndexes
    Future {
      val path = UniversePackageCache.getPackagePath(
        repoDir,
        repoIndex(),
        packageName,
        packageVersion)
      readPackageFiles(path)
    }
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: String
  ): Future[PackageFiles] = {
    Future {
      readPackageFiles(getPackagePath(packageName, releaseVersion))
    }
  }

  override def getPackageIndex(
    packageName: String
  ): Future[PackageInfo] = {
    Future {
      repoIndex().getPackages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(packageInfo) => packageInfo
      }
    }
  }

  override def getPackageDescribe(
    describe: DescribeRequest
  ): Future[Output[Json]] = {
    describe.packageVersions match {
      case Some(_) =>
        getPackageIndex(describe.packageName).map { packageInfo =>
          Ok(packageInfo.versions.asJson)
        }
      case None =>
        getPackageByPackageVersion(
          describe.packageName,
          describe.packageVersion
        ).map { packageFiles =>
          Ok(packageFiles.describeAsJson)
        }
    }
  }

  override def getRepoIndex: Future[UniverseIndex] = {
    Future {
      repoIndex()
    }
  }

  private[this] def repoIndex(): UniverseIndex = {
    val indexFile = repoDir.resolve(Paths.get("meta", "index.json"))

    UniversePackageCache.parseJsonFile(indexFile) match {
      case None => throw IndexNotFound(source)
      case Some(index) =>
        index.as[UniverseIndex].getOrElse(throw PackageFileSchemaMismatch("index.json"))
    }
  }

  private[this] def getPackagePath(packageName: String, releaseVersion: String): Path = {
    repoDir.resolve(
      Paths.get(
        "packages",
        packageName.charAt(0).toUpper.toString,
        packageName,
        releaseVersion))
  }

  private[this] def readPackageFiles(packageDir: Path): PackageFiles = {

    val packageJson = UniversePackageCache.parseJsonFile(
      packageDir.resolve("package.json")
    ).getOrElse {
      throw PackageFileMissing("package.json")
    }
    val mustache = UniversePackageCache.readFile(
      packageDir.resolve("marathon.json.mustache")
    ).getOrElse {
      throw PackageFileMissing("marathon.json.mustache")
    }

    PackageFiles.validate(
      packageDir.getFileName.toString,
      source,
      UniversePackageCache.parseJsonFile(packageDir.resolve("command.json")).getOrElse(Json.empty),
      UniversePackageCache.parseJsonFile(packageDir.resolve("config.json")).getOrElse(Json.empty),
      mustache,
      packageJson,
      UniversePackageCache.parseJsonFile(packageDir.resolve("resource.json")).getOrElse(Json.empty)
    ) match {
      case Invalid(err) => throw NelErrors(err)
      case Valid(valid) => valid
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
    Future(new ZipInputStream(universeBundle.toURI.toURL.openStream())).flatMap { bundleStream =>
      extractBundle(bundleStream, universeDir).ensure(bundleStream.close())
    } map { _ =>
      val rootDir = Files.list(universeDir).findFirst().get()
      new UniversePackageCache(rootDir.resolve(Paths.get("repo")), universeBundle)
    }
  }

  private[this] def extractBundle(bundle: ZipInputStream, cacheDir: Path): Future[Unit] = {
    val entries = Iterator.continually(Option(bundle.getNextEntry()))
      .takeWhile(_.isDefined)
      .flatten

    Future {
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

  // return path of specified version, or latest if version not specified
  private def getPackagePath(
    repository: Path,
    universeIndex: UniverseIndex,
    packageName: String,
    packageVersion: Option[String]): Path = {

      universeIndex.getPackages.get(packageName) match {
        case None => throw PackageNotFound(packageName)
        case Some(packageInfo) =>
          val version = packageVersion.getOrElse(packageInfo.currentVersion)
          packageInfo.versions.get(version) match {
            case None => throw VersionNotFound(packageName, version)
            case Some(revision) =>
              val packagePath = Paths.get(
                "packages",
                packageName.charAt(0).toUpper.toString,
                packageName,
                revision)
              repository.resolve(packagePath)
          }
      }
  }

  private def parseJsonFile(file: Path): Option[Json] = {
    readFile(file).map { content =>
      parse(content) match {
        case Left(err) => throw PackageFileNotJson(file.getFileName.toString, err.message)
        case Right(json) => json
      }
    }
  }

  private def readFile(path: Path): Option[String] = {
    if (path.toFile.exists()) {
      try {
        Some(new String(Files.readAllBytes(path), Charsets.Utf8))
      } catch {
        case e: Throwable =>
          // TODO: Make sure that we pass along the cause e!
          throw new PackageFileMissing(path.toString)
      }
    } else {
      None
    }
  }
}
