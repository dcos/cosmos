package com.mesosphere.cosmos

import java.nio.file._
import java.util.zip.ZipInputStream

import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.data.Xor.{Left, Right}
import cats.std.list._
import cats.syntax.apply._
import cats.syntax.option._
import cats.syntax.traverse._
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
  ): Future[CosmosResult[PackageFiles]] = {
    getRepoIndex(repoDir) // this will eventually be allIndexes
      .flatMapXor { (repoIndex: UniverseIndex) =>
        Future.value(getPackagePath(repoDir, repoIndex, packageName, version))
          .flatMapXor((path: Path) => readPackageFiles(path, repoIndex.version))
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

  private def getRepoIndex(repository: Path): Future[CosmosResult[UniverseIndex]] = {
    val indexFile = repository.resolve(Paths.get("meta", "index.json"))
    parseJsonFile(indexFile)
      .map { indexValidated =>
        indexValidated
          .toXor
          .leftMap(_ => errorNel(IndexNotFound))
          .flatMap { index =>
            index
              .getOrElse(Json.obj())    // Json.obj() is {}, Json.empty is null
              .as[UniverseIndex]
              .leftMap(_ => errorNel(PackageFileSchemaMismatch("index.json")))
          }
      }
  }

  // return path of specified version, or latest if version not specified
  private def getPackagePath(
    repository: Path,
    universeIndex: UniverseIndex,
    packageName: String,
    packageVersion: Option[String]): CosmosResult[Path] = {

    universeIndex.getPackages.get(packageName) match {
      case None => Left(errorNel(PackageNotFound(packageName)))
      case Some(packageInfo) =>

        val version = packageVersion.getOrElse(packageInfo.currentVersion)
        packageInfo.versions.get(version) match {
          case None => Left(errorNel(VersionNotFound(packageName, version)))
          case Some(revision) =>
            val packagePath = Paths.get("packages",
                                        packageName.charAt(0).toUpper.toString,
                                        packageName,
                                        revision)
            Right(repository.resolve(packagePath))
        }
    }
  }

  private def readPackageFiles(
    packageDir: Path,
    version: String
  ): Future[CosmosResult[PackageFiles]] = {
    val jsonFilesList = List("command", "config", "package", "resource")
    val jsonFiles = Future.collect {
      jsonFilesList.map { file =>
        parseJsonFile(packageDir.resolve(s"${file}.json"))
      }
    }

    val marathonJsonMustache =
      readFile(packageDir.resolve("marathon.json.mustache"))
        .map(_.toValid(errorNel(PackageFileMissing("marathon.json.mustache"))))

    Future
      .join(jsonFiles, marathonJsonMustache)
      .map { case (jsonPackageFiles, mustacheValid) =>

        (jsonPackageFiles.toList.sequenceU |@| mustacheValid).tupled.toXor.flatMap {
          case (List(commandJsonOpt, configJsonOpt, packageJsonOpt, resourceJsonOpt), mustache) =>

            packageJsonOpt
              .toRightXor(errorNel(PackageFileMissing("package.json")))
              .flatMap { packageJson =>
                val revision = packageDir.getFileName.toString
                val commandJson = commandJsonOpt.getOrElse(Json.empty)
                val configJson = configJsonOpt.getOrElse(Json.empty)
                val resourceJson = resourceJsonOpt.getOrElse(Json.empty)

                PackageFiles
                  .validate(
                    version, revision, commandJson, configJson, mustache, packageJson, resourceJson
                  )
                  .toXor
              }
        }
      }
  }

  private def parseJsonFile(file: Path): Future[ValidatedNel[CosmosError, Option[Json]]] = {
    readFile(file).map { fileOpt =>
      fileOpt match {
        case None => valid(None)
        case Some(content) =>
          parse(content)
            .leftMap(err => PackageFileNotJson(file.getFileName.toString, err.message))
            .toValidated.toValidatedNel
            .map((json: Json) => Some(json))
       }
    }
  }

  private def readFile(path: Path): Future[Option[String]] = {
    Future(Some(Files.readAllBytes(path)))
      .handle { case _: NoSuchFileException => None }
      .map(_.map((bytes: Array[Byte]) => new String(bytes, Charsets.Utf8)))
  }

}
