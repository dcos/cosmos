package com.mesosphere.cosmos

import java.io.{StringReader, StringWriter, File}
import java.net.URI
import java.nio.file.{Paths, Files, Path}
import java.util.zip.ZipInputStream

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.finagle.Http
import com.twitter.io.Charsets
import com.twitter.util.Try
import io.circe._
import io.circe.parse.{parse => parseJson}
import cats.data.Xor.Right
import cats.std.list._

import scala.collection.JavaConverters._

/** Retrieves packages from the Universe GitHub repo and caches them in the local filesystem. */
final class UniversePackageCache private(repoPackagesDir: Path) extends PackageCache {

  import UniversePackageCache._

  override def get(packageName: String): Try[Option[String]] = {
    Try {
      val packageDir = getLatestPackageVersion(packageName, repoPackagesDir)
      val templateFile = readPackageFile(packageDir, "marathon.json.mustache")
      val configJson = readPackageFile(packageDir, "config.json")
      val marathonJson = renderConfig(templateFile, configJson)
      Some(marathonJson)
    }
  }

}

object UniversePackageCache {

  val MustacheFactory = new DefaultMustacheFactory()

  def apply(universeBundle: URI, universeDir: Path): Try[UniversePackageCache] = {
    Try {
      val bundleStream = universeBundle.toURL.openStream()
      val zipStream = new ZipInputStream(bundleStream)

      val rootDir = try {
        extractBundle(zipStream, universeDir)
      } finally {
        zipStream.close()
      }

      new UniversePackageCache(rootDir.resolve(Paths.get("repo", "packages")))
    }
  }

  private[this] def extractBundle(bundle: ZipInputStream, universeDir: Path): Path = {
    var rootDir: Option[Path] = None

    while (true) {
      Option(bundle.getNextEntry()) match {
        case Some(entry) =>
          try {
            val entryPath = universeDir.resolve(entry.getName)
            if (entry.isDirectory) {
              Files.createDirectory(entryPath)

              if (rootDir.isEmpty) {
                rootDir = Some(entryPath)
              }
            } else {
              Files.copy(bundle, entryPath)
            }
          } finally {
            bundle.closeEntry()
          }
        case _ =>
          return rootDir.getOrElse(throw new IllegalStateException("No root directory in bundle"))
      }
    }

    throw new AssertionError("Unreachable")
  }

  private def getLatestPackageVersion(packageName: String, repository: Path): Path = {
    val packagePath = Paths.get(packageName.charAt(0).toUpper.toString, packageName)
    val versionsDir = repository.resolve(packagePath)
    val dirEntries = Files.newDirectoryStream(versionsDir)

    try {
      dirEntries
        .iterator()
        .asScala
        .maxBy(_.getFileName.toString.toInt)
    } finally {
      dirEntries.close()
    }
  }

  private def readPackageFile(packageDir: Path, fileName: String): String = {
    new String(Files.readAllBytes(packageDir.resolve(fileName)), Charsets.Utf8)
  }

  def renderConfig(template: String, configJson: String): String = {
    val output = new StringWriter()
    val templateReader = new StringReader(template)
    val mustache = MustacheFactory.compile(templateReader, "marathon.json.mustache")

    val parsedConfig = parseJson(configJson)
    val configMap = parsedConfig.getOrElse(Json.empty).as[Map[String, Json]].getOrElse(Map.empty)
    val propertiesMap = configMap.getOrElse("properties", Json.empty)
    val defaultsJson = extractDefaults(propertiesMap)
    val defaultsMap = defaultsJson.as[Map[String, Json]].getOrElse(Map.empty).mapValues(jsonToScala)

    mustache.execute(output, defaultsMap.asJava)
    output.toString
  }

  def extractDefaults(properties: Json): Json = {
    properties.mapObject { propertiesObject =>
      val extractedMap = propertiesObject.toMap.flatMap { case (propertyName, propertyJson) =>
          propertyJson.asObject.flatMap { propertyObject =>
            propertyObject("default").orElse {
              propertyObject("properties").map(extractDefaults)
            }
          }
          .map(propertyName -> _)
      }
      JsonObject.from(extractedMap.toList)
    }
  }

  def jsonToScala(json: Json): Any = {
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = _.toDouble,
      jsonString = identity,
      jsonArray = _.map(jsonToScala),
      jsonObject = _.toMap.mapValues(jsonToScala)
    )
  }

}
