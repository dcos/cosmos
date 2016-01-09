package com.mesosphere.cosmos

import java.io._
import java.net.URI
import java.nio.file._
import java.util.zip.ZipInputStream

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.parse.parse
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

/** Stores packages from the Universe GitHub repository in the local filesystem.
  *
  * @param repoPackagesDir the local cache of the repository's `repo/packages` directory
  */
final class UniversePackageCache private(repoPackagesDir: Path) extends PackageCache {

  import UniversePackageCache._

  override def get(packageName: String): Future[Option[String]] = {
    getLatestPackageVersion(packageName, repoPackagesDir)
      .flatMapOption(renderTemplate)
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
  def apply(universeBundle: URI, universeDir: Path): Future[UniversePackageCache] = {
    Future(new ZipInputStream(universeBundle.toURL.openStream()))
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
  ): Future[Option[Path]] = {
    val packagePath = Paths.get(packageName.charAt(0).toUpper.toString, packageName)
    val versionsDir = repository.resolve(packagePath)

    Future(Some(Files.newDirectoryStream(versionsDir)))
      .handle {
        case _: NoSuchFileException => None
      }
      .flatMapOption { (dirEntries: DirectoryStream[Path]) =>
        Future {
          dirEntries
            .iterator()
            .asScala
            .maxBy(_.getFileName.toString.toInt)
        }.ensure(dirEntries.close())
      }
  }

  private def renderTemplate(packageDir: Path): Future[String] = {
    val templateFileName = "marathon.json.mustache"
    val templatePath = packageDir.resolve(templateFileName)
    val mustacheFut = Future(Files.newBufferedReader(templatePath))
      .map(MustacheFactory.compile(_, templateFileName))

    val configPath = packageDir.resolve("config.json")
    val scopeConfig = readFile(configPath)
      .mapOption(extractDefaultsFromConfig)
      .map(_.getOrElse(Map.empty))

    val resourcePath = packageDir.resolve("resource.json")
    val scopeUris = readFile(resourcePath)
      .mapOption(extractResources)
      .map(_.getOrElse(Map.empty))

    Future.join(mustacheFut, scopeConfig, scopeUris)
      .map { case (mustache, config, uris) =>
        val output = new StringWriter()
        val params = (config ++ uris).mapValues(jsonToJava).asJava
        mustache.execute(output, params)
        output.toString
      }
  }

  private def readFile(path: Path): Future[Option[String]] = {
    Future(Some(Files.readAllBytes(path)))
      .handle {
        case t => None
      }
      .mapOption((bytes: Array[Byte]) => new String(bytes, Charsets.Utf8))
  }

  private def extractResources(resourceJson: String): Map[String, Json] = {
    val assets = parse(resourceJson)
      .getOrElse(Json.empty)
      .cursor
      .downField("assets")
      .map(_.focus)
      .getOrElse(Json.empty)

    Map("resource" -> Json.obj("assets" -> assets))
  }

  private def extractDefaultsFromConfig(configJson: String): Map[String, Json] = {
    val topProperties = parse(configJson)
      .getOrElse(Json.empty)
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

}
