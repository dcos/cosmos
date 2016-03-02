package com.mesosphere.cosmos

import java.nio.file._

import cats.data._
import cats.std.list._           // allows for traversU in verifySchema
import cats.std.option._
import cats.syntax.apply._       // provides |@|
import cats.syntax.option._
import cats.syntax.traverse._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.model.SearchResult
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe._
import com.netaporter.uri.Uri
import com.twitter.util.Future
import io.circe.{Decoder, Json}

import scala.util.matching.Regex

/** Stores packages from the Universe GitHub repository in the local filesystem.
  */
final class UniversePackageCache private (
  override val uri: Uri,
  repoDir: Path,
  universeIndex: UniverseIndex,
  packageMetadataStore: PackageMetadataStore
) extends Repository {

  import UniversePackageCache._

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    Future {
      val packageDir = getPackagePath(packageName, packageVersion)
      readPackageFiles(packageDir)
    }
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles] = {
    Future {
      val packageDir = getPackageReleasePath(packageName, releaseVersion)
      readPackageFiles(packageDir)
    }
  }

  override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
    Future(universeIndex.getPackages.getOrElse(packageName, throw PackageNotFound(packageName)))
  }

  override def search(queryOpt: Option[String]): Future[List[SearchResult]] = {
    Future {
      searchIndex(queryOpt)
        .map { indexEntry =>
          val resources = packageResources(indexEntry.name)
          searchResult(indexEntry, resources.images)
        }
    }
  }

  // return path of specified version, or latest if version not specified
  private[this] def getPackagePath(packageName: String, packageVersion: Option[PackageDetailsVersion]): Path = {
    universeIndex.getPackages.get(packageName) match {
      case None => throw PackageNotFound(packageName)
      case Some(packageInfo) =>
        val version = packageVersion.getOrElse(packageInfo.currentVersion)
        packageInfo.versions.get(version) match {
          case None => throw VersionNotFound(packageName, version)
          case Some(revision) =>
            val packagePath = getPackageReleasePath(packageName, revision)
            repoDir.resolve(packagePath)
        }
    }
  }

  private[this] def getPackageReleasePath(packageName: String, releaseVersion: ReleaseVersion): Path = {
    repoDir.resolve(
      Paths.get(
        "packages",
        packageName.charAt(0).toUpper.toString,
        packageName,
        releaseVersion.toString
      )
    )
  }

  private[this] def readPackageFiles(packageDir: Path): PackageFiles = {

    val packageJson = packageMetadataStore.parseJsonFile(
      packageDir.resolve("package.json")
    ).getOrElse {
      throw PackageFileMissing("package.json")
    }
    val mustache = packageMetadataStore.readUtf8File(
      packageDir.resolve("marathon.json.mustache")
    ).getOrElse {
      throw PackageFileMissing("marathon.json.mustache")
    }

    val packageDefValid = verifySchema[PackageDetails](packageJson, "package.json")
    val resourceDefValid = decodeJsonFile[Resource](packageDir, "resource.json").toValidated.toValidatedNel
    val commandJsonValid = decodeJsonFile[Command](packageDir, "command.json").toValidated.toValidatedNel
    val configJsonObject = packageMetadataStore.parseJsonFile(packageDir.resolve("config.json"))
      .traverseU { json =>
        json
          .asObject
          .toValidNel[CosmosError](PackageFileSchemaMismatch("config.json"))
      }

    (packageDefValid |@| resourceDefValid |@| commandJsonValid |@| configJsonObject)
      .map { (packageDef, resourceDef, commandJson, configJson) =>
        PackageFiles(
          packageDir.getFileName.toString,
          uri,
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

  private[this] def searchIndex(queryOpt: Option[String]): List[UniverseIndexEntry] = {
    queryOpt match {
      case None => universeIndex.packages
      case Some(query) =>
        if (query.contains("*")) {
          val regex = getRegex(query)
          universeIndex.packages.filter(searchRegexInPackageIndex(_, regex))
        } else {
          universeIndex.packages.filter(searchPackageIndex(_, query.toLowerCase()))
        }
    }
  }

  private[this] def packageResources(packageName: String): Resource = {
    val packageDir = getPackagePath(packageName, packageVersion = None)
    decodeJsonFile[Resource](packageDir, "resource.json")
      .valueOr(err => throw err)
      .getOrElse(Resource())
  }

  private[this] def decodeJsonFile[A : Decoder](
    packageDir: Path,
    fileName: String
  ): Xor[CosmosError, Option[A]] = {
    packageMetadataStore.parseJsonFile(packageDir.resolve(fileName)).traverseU { json =>
      json.as[A].leftMap(_ => PackageFileSchemaMismatch(fileName))
    }
  }

}

object UniversePackageCache {

  def apply(uri: Uri, dataDir: Path, packageMetadataStore: PackageMetadataStore): UniversePackageCache = {
    val repoDir = dataDir.resolve("repo")
    val indexFile = repoDir.resolve(Paths.get("meta", "index.json"))

    val repoIndex = packageMetadataStore.parseJsonFile(indexFile)
      .toRightXor(new IndexNotFound(uri))
      .flatMap { index =>
        index.as[UniverseIndex].leftMap(_ => PackageFileSchemaMismatch("index.json"))
      }
      .valueOr(err => throw err)

    val repoVersion = repoIndex.version
    if (repoVersion.toString.startsWith("2.")) {
      new UniversePackageCache(uri, repoDir, repoIndex, packageMetadataStore)
    } else {
      throw new UnsupportedRepositoryVersion(repoVersion)
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
      promoted = indexEntry.promoted,
      images = images
    )
  }

  private def searchRegexInPackageIndex(index: UniverseIndexEntry, regex: Regex): Boolean = {
    regex.findFirstIn(index.name).isDefined ||
      regex.findFirstIn(index.description).isDefined ||
      index.tags.exists(regex.findFirstIn(_).isDefined)
  }

  private def searchPackageIndex(index: UniverseIndexEntry, query: String): Boolean= {
    index.name.toLowerCase().contains(query) ||
      index.description.toLowerCase().contains(query) ||
      index.tags.exists(_.toLowerCase().contains(query))
  }

  private def getRegex(query: String): Regex = {
    s"""^${query.replaceAll("\\*", ".*")}$$""".r
  }

  private def verifySchema[A: Decoder](
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
