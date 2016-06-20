package com.mesosphere.cosmos.repository

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.{Path, Paths}
import java.util.Properties
import java.util.zip.{GZIPInputStream, ZipInputStream}

import cats.data.Xor
import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.MediaTypeOps._
import com.mesosphere.cosmos.http.{MediaTypeParseError, MediaTypeParser, MediaTypes}
import com.mesosphere.cosmos._
import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v3.circe.Decoders._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.io.StreamIO
import com.twitter.util.Future
import io.circe.{Decoder, DecodingFailure, Json, JsonObject}
import io.circe.parse._

import scala.io.Source
import scala.util.{Failure, Success}

trait UniverseClient extends ((Uri, universe.v3.model.DcosReleaseVersion) => Future[internal.model.CosmosInternalRepository])

object UniverseClient {
  private[this] final class FnUniverseClient(
      function: (Uri, universe.v3.model.DcosReleaseVersion) => Future[internal.model.CosmosInternalRepository])
      extends UniverseClient {
    override def apply(uri: Uri, dcosReleaseVersion: universe.v3.model.DcosReleaseVersion) = function(uri, dcosReleaseVersion)
  }

  def apply()(implicit statsReceiver: StatsReceiver = NullStatsReceiver): UniverseClient = {
    new DefaultUniverseClient
  }

  def apply(function: (Uri, universe.v3.model.DcosReleaseVersion) => Future[internal.model.CosmosInternalRepository]): UniverseClient = {
    new FnUniverseClient(function)
  }
}

final class DefaultUniverseClient(
    implicit statsReceiver: StatsReceiver = NullStatsReceiver) extends UniverseClient {

  private[this] val stats = statsReceiver.scope("repositoryFetcher")

  private[this] val cosmosVersion = {
    val props = new Properties()
    val is = this.getClass.getResourceAsStream("/build.properties")
    if (is != null) {
      props.load(is)
      is.close()
    }
    Option(props.getProperty("cosmos.version")).getOrElse("unknown-version")
  }

  override def apply(
      uri: Uri,
      dcosReleaseVersion: universe.v3.model.DcosReleaseVersion
  ): Future[internal.model.CosmosInternalRepository] = {
    Stat.timeFuture(stats.stat("fetch")) {
      Future { uri.toURI.toURL.openConnection() } flatMap { conn =>
        // Set headers on request
        conn.setRequestProperty("Accept", MediaTypes.UniverseV3Repository.show)
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty(
            "User-Agent",
            s"cosmos/$cosmosVersion dcos/${dcosReleaseVersion.show}"
        )

        // Handle the response
        Future {
          val contentType =
            Option(conn.getHeaderField("Content-Type")).getOrElse(
                throw UnsupportedContentType(
                    List(MediaTypes.UniverseV3Repository,
                         MediaTypes.applicationZip)
                )
            )
          val parsedContentType = MediaTypeParser
            .parse(contentType)
            .handle {
              case MediaTypeParseError(msg, cause) =>
                throw UnsupportedContentType(
                    List(MediaTypes.UniverseV3Repository,
                         MediaTypes.applicationZip)
                )
            }
            .get
          val contentEncoding = Option(conn.getHeaderField("Content-Encoding"))
          (parsedContentType, contentEncoding)
        } map {
          case (contentType, contentEncoding) =>
            val in = contentEncoding match {
              case Some("gzip") => new GZIPInputStream(conn.getInputStream)
              case ce @ Some(_) =>
                throw UnsupportedContentEncoding(List("gzip"), ce)
              case _ => conn.getInputStream
            }

            if (contentType.isCompatibleWith(MediaTypes.UniverseV3Repository)) {
              decode[universe.v3.model.Repository](
                  Source.fromInputStream(in).mkString) match {
                case Xor.Left(err) => throw CirceError(err)
                case Xor.Right(repo) => repo
              }
            } else if (contentType.isCompatibleWith(MediaTypes.applicationZip)) {
              processUniverseV2(uri, in)
            } else {
              throw UnsupportedContentType.forMediaType(
                  List(MediaTypes.UniverseV3Repository,
                       MediaTypes.applicationZip),
                  Some(contentType)
              )
            }
        }
      } map { repo =>
        internal.model.CosmosInternalRepository(
            repo.packages.map(_.as[internal.model.PackageDefinition]).sorted.reverse
        )
      }
    }
  }

  private[this] case class V2PackageInformation(
      packageDetails: Option[universe.v2.model.PackageDetails] = None,
      marathonMustache: Option[ByteBuffer] = None,
      command: Option[universe.v2.model.Command] = None,
      config: Option[JsonObject] = None,
      resource: Option[universe.v2.model.Resource] = None
  )

  private[this] def processUniverseV2(
      sourceUri: Uri,
      inputStream: InputStream
  ): universe.v3.model.Repository = {
    val bundle = new ZipInputStream(inputStream)
    // getNextEntry() returns null when there are no more entries
    val universeRepository = Iterator.continually {
      // Note: this closure is not technically pure. The variable bundle is mutated here.
      Option(bundle.getNextEntry())
    }.takeWhile(_.isDefined)
      .flatten
      .filter(!_.isDirectory)
      .map(entry => Paths.get(entry.getName))
      .filter(entryPath => entryPath.getNameCount == 7) // Only keep leaf nodes
      .foldLeft(Map.empty[(String, Int), V2PackageInformation]) {
        (state, entryPath) =>
          val packageName = entryPath.getName(4).toString
          val releaseVersion = Integer.parseInt(entryPath.getName(5).toString)

          val oldV2Package = state.getOrElse(
              (packageName, releaseVersion),
              V2PackageInformation()
          )

          // Note: this closure is not technically pure. The variable bundle is muted here.
          val buffer = StreamIO.buffer(bundle).toByteArray()

          val newV2Package = {
            if (entryPath.getFileName() == Paths.get("package.json")) {
              oldV2Package.copy(
                  packageDetails = Some(
                      parseAndVerify[universe.v2.model.PackageDetails](
                          entryPath, new String(buffer))
                  )
              )
            } else if (entryPath.getFileName() == Paths.get(
                           "marathon.json.mustache")) {
              oldV2Package.copy(
                  marathonMustache = Some(ByteBuffer.wrap(buffer)))
            } else if (entryPath.getFileName() == Paths.get("config.json")) {
              oldV2Package.copy(
                  config = Some(
                      parseJson(entryPath, new String(buffer)).asObject
                        .getOrElse(
                          throw PackageFileSchemaMismatch(
                              "config.json", DecodingFailure("Object", List()))
                      )
                  )
              )
            } else if (entryPath.getFileName() == Paths.get("resource.json")) {
              oldV2Package.copy(
                  resource = Some(
                      parseAndVerify[universe.v2.model.Resource](
                          entryPath, new String(buffer))
                  )
              )
            } else if (entryPath.getFileName() == Paths.get("command.json")) {
              oldV2Package.copy(
                  command = Some(
                      parseAndVerify[universe.v2.model.Command](
                          entryPath, new String(buffer))
                  )
              )
            } else {
              // We have an extra file for some reason. Just ignore it.
              oldV2Package
            }
          }

          state + (((packageName, releaseVersion), newV2Package))
      }

    universe.v3.model.Repository(
        universeRepository.toList.map {
          case ((packageName, releaseVersion), packageInfo) =>
            val details = packageInfo.packageDetails.getOrElse(
                throw PackageFileMissing("package.json"))
            val marathon = packageInfo.marathonMustache.getOrElse(
                throw PackageFileMissing("marathon.json.mustache"))

            universe.v3.model.V2Package(
                universe.v3.model.V2PackagingVersion.instance,
                details.name,
                universe.v3.model.PackageDefinition
                  .Version(details.version.toString),
                universe.v3.model.PackageDefinition
                  .ReleaseVersion(releaseVersion),
                details.maintainer,
                details.description,
                universe.v3.model.Marathon(marathon),
                details.tags.map {
                  tag =>
                    /* TODO(version): This method throws an AssertionError. This
                     * probably turns into a 500 in the RPC. We need to fix this.
                     */
                    universe.v3.model.PackageDefinition.Tag(tag)
                },
                details.selected.orElse(Some(false)),
                details.scm,
                details.website,
                details.framework,
                details.preInstallNotes,
                details.postInstallNotes,
                details.postUninstallNotes,
                details.licenses.map {
                  licenses =>
                    licenses.flatMap {
                      license =>
                        v3LicenseToV2License.invert(license) match {
                          case Success(l) => Some(l)
                          case Failure(err) => None
                        }
                    }
                },
                packageInfo.resource.as[Option[universe.v3.model.V2Resource]],
                packageInfo.config,
                packageInfo.command.as[Option[universe.v3.model.Command]]
            )
        }
    )
  }

  private[this] def parseAndVerify[A: Decoder](
      path: Path,
      content: String
  ): A = {
    parseJson(path, content).as[A] match {
      case Left(err) => throw PackageFileSchemaMismatch(path.toString, err)
      case Right(right) => right
    }
  }

  private[this] def parseJson(
      path: Path,
      content: String
  ): Json = {
    parse(content) match {
      case Left(err) => throw PackageFileNotJson(path.toString, err.message)
      case Right(right) => right
    }
  }
}
