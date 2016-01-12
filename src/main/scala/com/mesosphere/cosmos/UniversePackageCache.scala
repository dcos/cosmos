package com.mesosphere.cosmos

import java.io._
import java.nio.file._
import java.util.zip.ZipInputStream
import java.util.{Base64, Map => JavaMap}

import cats.data.Validated.valid
import cats.data.ValidatedNel
import cats.data.Xor.{Left, Right}
import cats.std.list._
import cats.syntax.apply._
import cats.syntax.option._
import cats.syntax.traverse._
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.model._
import com.netaporter.uri.Uri
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.generic.auto._
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

/** Stores packages from the Universe GitHub repository in the local filesystem.
  *
  * @param repoPackagesDir the local cache of the repository's `repo/packages` directory
  */
final class UniversePackageCache private(repoPackagesDir: Path) extends PackageCache {

  import UniversePackageCache._

  override def getMarathonJson(packageName: String): Future[CosmosResult[Json]] = {
    getLatestPackageVersion(packageName, repoPackagesDir)
      .flatMapXor(readPackageFiles)
      .map { packageFilesXor =>
        packageFilesXor.flatMap { packageFiles =>
          renderMustacheTemplate(packageFiles)
            .flatMap(addLabels(_, packageFiles))
        }
      }
  }
}

object UniversePackageCache {

  val MustacheFactory = new DefaultMustacheFactory()

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
        new UniversePackageCache(rootDir.resolve(Paths.get("repo", "packages")))
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

  private def getLatestPackageVersion(
    packageName: String,
    repository: Path
  ): Future[CosmosResult[Path]] = {
    val packagePath = Paths.get(packageName.charAt(0).toUpper.toString, packageName)
    val versionsDir = repository.resolve(packagePath)

    Future(Right(Files.newDirectoryStream(versionsDir)))
      .handle {
        case _: NoSuchFileException => Left(errorNel(PackageNotFound(packageName)))
      }
      .flatMapXor { (dirEntries: DirectoryStream[Path]) =>
        Future {
          Right {
            dirEntries
              .iterator()
              .asScala
              .maxBy(_.getFileName.toString.toInt)
          }
        }.ensure(dirEntries.close())
      }
  }

  private def readPackageFiles(packageDir: Path): Future[CosmosResult[PackageFiles]] = {
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
                val commandJson = commandJsonOpt.getOrElse(Json.empty)
                val configJson = configJsonOpt.getOrElse(Json.empty)
                val resourceJson = resourceJsonOpt.getOrElse(Json.empty)

                PackageFiles
                  .validate(commandJson, configJson, mustache, packageJson, resourceJson)
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

  private def renderMustacheTemplate(packageFiles: PackageFiles): CosmosResult[Json] = {
    val strReader = new StringReader(packageFiles.marathonJsonMustache)
    val mustache = MustacheFactory.compile(strReader, "marathon.json.mustache")

    // Build a java.util.HashMap manually, since we need a mutable map to insert the "resource" key
    // This should be cleaned up by GitHub issue #62
    val defaults = extractDefaultsFromConfig(packageFiles.configJson).mapValues(jsonToJava)
    val params = new java.util.HashMap[String, Any]()
    defaults.foreach { case (k, v) => params.put(k, v) }
    params.put("resource", extractAssetsAsMap(packageFiles.resourceJson))

    val output = new StringWriter()
    mustache.execute(output, params)
    parse(output.toString)
      .leftMap(err => errorNel(PackageFileNotJson("marathon.json", err.message)))
  }

  private def readFile(path: Path): Future[Option[String]] = {
    Future(Some(Files.readAllBytes(path)))
      .handle { case _: NoSuchFileException => None }
      .map(_.map((bytes: Array[Byte]) => new String(bytes, Charsets.Utf8)))
  }

  private def extractAssetsAsMap(resource: Resource): JavaMap[String, _] = {
    val assets = resource.assets.getOrElse(Assets.empty)

    val dockerMap = assets.container.getOrElse(Container.empty).docker.asJava
    val containerMap = Map("docker" -> dockerMap).asJava
    val urisMap = assets.uris.getOrElse(Map.empty).asJava

    val assetsMap = Map("uris" -> urisMap, "container" -> containerMap).asJava
    Map("assets" -> assetsMap).asJava
  }

  private def extractDefaultsFromConfig(configJson: Json): Map[String, Json] = {
    val topProperties = configJson
      .cursor
      .downField("properties")
      .map(_.focus)
      .getOrElse(Json.empty)

    filterDefaults(topProperties)
      .as[Map[String, Json]]
      .getOrElse(Map.empty)
  }

  private def filterDefaults(properties: Json): Json = {
    val defaults = properties
      .asObject
      .getOrElse(JsonObject.empty)
      .toMap
      .flatMap { case (propertyName, propertyJson) =>
        propertyJson
          .asObject
          .flatMap { propertyObject =>
            propertyObject("default").orElse {
              propertyObject("properties").map(filterDefaults)
            }
          }
          .map(propertyName -> _)
      }

    Json.fromJsonObject(JsonObject.fromMap(defaults))
  }

  private def jsonToJava(json: Json): Any = {
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toInt.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(jsonToJava).asJava,
      jsonObject = _.toMap.mapValues(jsonToJava).asJava
    )
  }

  private def addLabels(marathonJson: Json, packageFiles: PackageFiles): CosmosResult[Json] = {
    // add images to package.json metadata for backwards compatability in the UI
    val packageDef = packageFiles.packageJson.copy(images = packageFiles.resourceJson.images)

    // Circe populates omitted fields with null values; remove them (see GitHub issue #56)
    val packageJson = packageDef.asJson.mapObject { obj =>
      JsonObject.fromMap(obj.toMap.filterNot { case (k, v) => v.isNull })
    }
    val packageBytes = packageJson.noSpaces.getBytes(Charsets.Utf8)
    val packageMetadata = Base64.getEncoder.encodeToString(packageBytes)

    val commandBytes = packageFiles.commandJson.noSpaces.getBytes(Charsets.Utf8)
    val commandMetadata = Base64.getEncoder.encodeToString(commandBytes)

    val frameworkName = packageFiles.configJson.cursor
      .downField(packageDef.name)
      .flatMap(_.get[String]("framework-name").toOption)

    // insert labels
    val packageLabels: Map[String, String] = Seq(
      Some("DCOS_PACKAGE_METADATA" -> packageMetadata),
      Some("DCOS_PACKAGE_REGISTRY_VERSION" -> "2.0.0-rc1"), // TODO: take this from repo
      Some("DCOS_PACKAGE_NAME" -> packageDef.name),
      Some("DCOS_PACKAGE_VERSION" -> packageDef.version),
      Some("DCOS_PACKAGE_SOURCE" -> universeBundleUri().toString),
      Some("DCOS_PACKAGE_RELEASE" -> "0"), // TODO: fetch actually version
      Some("DCOS_PACKAGE_IS_FRAMEWORK" -> packageDef.framework.getOrElse(true).toString),
      Some("DCOS_PACKAGE_COMMAND" -> commandMetadata),
      frameworkName.map("PACKAGE_FRAMEWORK_NAME_KEY" -> _)
    ).flatten.toMap

    val existingLabels = marathonJson.cursor
      .get[Map[String, String]]("labels").getOrElse(Map.empty)

    Right(marathonJson.mapObject(_.+("labels", (existingLabels ++ packageLabels).asJson)))
  }
}
