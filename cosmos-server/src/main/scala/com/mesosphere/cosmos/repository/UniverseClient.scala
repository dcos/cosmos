package com.mesosphere.cosmos.repository

import java.io.{IOException, InputStream}
import java.net.{HttpURLConnection, MalformedURLException, URISyntaxException}
import java.nio.ByteBuffer
import java.nio.file.{Path, Paths}
import java.util.zip.{GZIPInputStream, ZipInputStream}

import cats.data.Xor
import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.http.MediaTypeOps._
import com.mesosphere.cosmos.http.{MediaType, MediaTypeParseError, MediaTypeParser, RequestSession}
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.MediaTypes
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.io.StreamIO
import com.twitter.util.{Future, Try => TwitterTry}
import io.circe.jawn._
import io.circe.{Decoder, DecodingFailure, Json, JsonObject}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Success, Try => ScalaTry}

trait UniverseClient {
  def apply(repository: PackageRepository)(implicit session: RequestSession): Future[universe.v3.model.Repository]
}

final class DefaultUniverseClient(adminRouter: AdminRouter)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) extends UniverseClient {

  import DefaultUniverseClient._

  private[this] val stats = statsReceiver.scope("repositoryFetcher")
  private[this] val fetchScope = stats.scope("fetch")

  private[this] val cosmosVersion = BuildProperties().cosmosVersion

  def apply(repository: PackageRepository)(implicit session: RequestSession): Future[universe.v3.model.Repository] = {
    adminRouter.getDcosVersion().flatMap { dcosVersion =>
      apply(repository, dcosVersion.version)
    }
  }

  // scalastyle:off cyclomatic.complexity method.length
  private[repository] def apply(
      repository: PackageRepository,
      dcosReleaseVersion: universe.v3.model.DcosReleaseVersion
  ): Future[universe.v3.model.Repository] = {
    fetchScope.counter("requestCount").incr()
    Stat.timeFuture(fetchScope.stat("histogram")) {
      Future { repository.uri.toURI.toURL.openConnection() } handle {
        case t @ (_: IllegalArgumentException | _: MalformedURLException | _: URISyntaxException) =>
          throw RepositoryUriSyntax(repository, t)
      } flatMap { case conn: HttpURLConnection =>
        // Set headers on request
        conn.setRequestProperty("Accept", MediaTypes.UniverseV3Repository.show)
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty(
          "User-Agent",
          s"cosmos/$cosmosVersion dcos/${dcosReleaseVersion.show}"
        )

        // Handle the response
        Future {

          conn.getResponseCode match {
            case HttpURLConnection.HTTP_OK =>
              fetchScope.scope("status").counter("200").incr()
              val contentType = parseContentType(Option(conn.getHeaderField("Content-Type"))).get
              val contentEncoding = Option(conn.getHeaderField("Content-Encoding"))
              (contentType, contentEncoding)
            case x @ (HttpURLConnection.HTTP_MOVED_PERM |
                      HttpURLConnection.HTTP_MOVED_TEMP |
                      HttpURLConnection.HTTP_SEE_OTHER |
                      TemporaryRedirect |
                      PermanentRedirect) =>
              fetchScope.scope("status").counter(x.toString).incr()
              // Different forms of redirect, HttpURLConnection won't follow a redirect across schemes
              val loc = Option(conn.getHeaderField("Location")).map(Uri.parse).flatMap(_.scheme)
              throw UnsupportedRedirect(List(repository.uri.scheme.get), loc)
            case x =>
              fetchScope.scope("status").counter(x.toString).incr()
              throw GenericHttpError("GET", repository.uri, x)
          }
        } handle {
          case t: IOException => throw RepositoryUriConnection(repository, t)
        } map { case (contentType, contentEncoding) =>
          contentEncoding match {
            case Some("gzip") =>
              fetchScope.scope("contentEncoding").counter("gzip").incr()
              (contentType, new GZIPInputStream(conn.getInputStream))
            case ce@Some(_) =>
              throw UnsupportedContentEncoding(List("gzip"), ce)
            case _ =>
              fetchScope.scope("contentEncoding").counter("plain").incr()
              (contentType, conn.getInputStream)
          }
        } map { case (contentType, bodyInputStream) =>
          decodeUniverse(contentType, bodyInputStream, repository.uri)
        }
      }
    }
  }
  // scalastyle:on cyclomatic.complexity method.length

  private[this] def decodeUniverse(
    contentType: MediaType,
    bodyInputStream: InputStream,
    repositoryUri: Uri
  ): universe.v3.model.Repository = {
    val decodeScope = fetchScope.scope("decode")
    if (contentType.isCompatibleWith(MediaTypes.UniverseV3Repository)) {
      val v3Scope = decodeScope.scope("v3")
      v3Scope.counter("count").incr()
      Stat.time(v3Scope.stat("histogram")) {
        decode[universe.v3.model.Repository](
          Source.fromInputStream(bodyInputStream).mkString
        ) match {
          case Xor.Left(err) => throw CirceError(err)
          case Xor.Right(repo) => repo
        }
      }
    } else if (contentType.isCompatibleWith(MediaTypes.UniverseV2Repository)) {
      val v2Scope = decodeScope.scope("v2")
      v2Scope.counter("count").incr()
      Stat.time(v2Scope.stat("histogram")) {
        processUniverseV2(repositoryUri, bodyInputStream)
      }
    } else {
      throw UnsupportedContentType.forMediaType(SupportedMediaTypes, Some(contentType))
    }
  }

  private[this] case class V2PackageInformation(
      packageDetails: Option[universe.v2.model.PackageDetails] = None,
      marathonMustache: Option[ByteBuffer] = None,
      command: Option[universe.v2.model.Command] = None,
      config: Option[JsonObject] = None,
      resource: Option[universe.v2.model.Resource] = None
  )

  private[this] case class V2ZipState(
    version: Option[universe.v2.model.UniverseVersion],
    packages: Map[(String, Int), Map[String, (Path, Array[Byte])]]
  )

  private[this] def processUniverseV2(
      sourceUri: Uri,
      inputStream: InputStream
  ): universe.v3.model.Repository = {
    val bundle = new ZipInputStream(inputStream)
    // getNextEntry() returns null when there are no more entries
    val universeRepository: V2ZipState = Iterator.continually {
      // Note: this closure is not technically pure. The variable bundle is mutated here.
      Option(bundle.getNextEntry())
    }.takeWhile(_.isDefined)
      .flatten
      .filter(!_.isDirectory)
      .map(entry => Paths.get(entry.getName))
      .filter(entryPath => entryPath.getNameCount > 2)
      .foldLeft(V2ZipState(None, Map.empty)) { (state, entryPath) =>
        // Note: this closure is not technically pure. The variable bundle is muted here.
        val buffer = StreamIO.buffer(bundle).toByteArray()
        processZipEntry(state, entryPath, buffer)
      }

    universeRepository.version match {
      case Some(version) if version.toString.startsWith("2.") => // Valid
      case Some(version) => throw UnsupportedRepositoryVersion(version)
      case _ => throw IndexNotFound(sourceUri)
    }

    val packageInfos = universeRepository.packages.mapValues(processPackageFiles)
    val packages = packageInfos.toList.map { case ((_, releaseVersion), packageInfo) =>
      buildV2Package(packageInfo, releaseVersion)
    }

    universe.v3.model.Repository(packages)
  }

  private[this] def processZipEntry(
    state: V2ZipState,
    entryPath: Path,
    buffer: Array[Byte]
  ): V2ZipState = {
    entryPath.asScala.map(_.toString).toList match {
      case _ :: _ :: "meta" :: "index.json" :: Nil =>
        val version = processIndex(entryPath, buffer)
        state.copy(version = state.version.orElse(Some(version)))
      case _ :: _ :: "packages" :: _ :: packageName :: releaseVersionString :: _ =>
        val releaseVersion = Integer.parseInt(releaseVersionString)
        val packageKey = (packageName, releaseVersion)

        val packageFiles =
          state.packages.getOrElse(packageKey, Map.empty) +
            (entryPath.getFileName.toString -> ((entryPath, buffer)))

        state.copy(packages = state.packages + ((packageKey, packageFiles)))
      case _ =>
        state
    }
  }

  private[this] def processPackageFiles(
    packageFiles: Map[String, (Path, Array[Byte])]
  ): V2PackageInformation = {
    V2PackageInformation(
      packageDetails = packageFiles.get("package.json").map { case (entryPath, buffer) =>
        parseAndVerify[universe.v2.model.PackageDetails](entryPath, new String(buffer))
      },
      marathonMustache = packageFiles.get("marathon.json.mustache").map { case (_, buffer) =>
        ByteBuffer.wrap(buffer)
      },
      command = packageFiles.get("command.json").map { case (entryPath, buffer) =>
        parseAndVerify[universe.v2.model.Command](entryPath, new String(buffer))
      },
      config = packageFiles.get("config.json").map { case (entryPath, buffer) =>
        parseJson(entryPath, new String(buffer))
          .asObject
          .getOrElse {
            throw PackageFileSchemaMismatch("config.json", DecodingFailure("Object", List()))
          }
      },
      resource = packageFiles.get("resource.json").map { case (entryPath, buffer) =>
        parseAndVerify[universe.v2.model.Resource](entryPath, new String(buffer))
      }
    )
  }

  private[this] def buildV2Package(
    packageInfo: V2PackageInformation,
    releaseVersion: Int
  ): universe.v3.model.V2Package = {
    val details = packageInfo.packageDetails.getOrElse(
      throw PackageFileMissing("package.json"))
    val marathon = packageInfo.marathonMustache.getOrElse(
      throw PackageFileMissing("marathon.json.mustache"))

    universe.v3.model.V2Package(
      universe.v3.model.V2PackagingVersion,
      details.name,
      universe.v3.model.PackageDefinition
        .Version(details.version.toString),
      releaseVersion.as[ScalaTry[universe.v3.model.PackageDefinition.ReleaseVersion]].get,
      details.maintainer,
      details.description,
      universe.v3.model.Marathon(marathon),
      details.tags.map {
        tag =>
          // The format of the tag is enforced by the json schema for universe packagingVersion 2.0 and 3.0
          // unfortunately com.mesosphere.universe.v2.model.PackageDetails#tags is a list string due to the
          // more formal type not being defined. The likely of this failing is remove, especially when the
          // source is universe-server.
          universe.v3.model.PackageDefinition.Tag(tag).get
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
                case Failure(_) => None
              }
          }
      },
      packageInfo.resource.as[Option[universe.v3.model.V2Resource]],
      packageInfo.config,
      packageInfo.command.as[Option[universe.v3.model.Command]]
    )
  }

  private[this] def processIndex(
    entryPath: Path,
    buffer: Array[Byte]
  ): universe.v2.model.UniverseVersion = {
    val decodedVersion = parseJson(entryPath, new String(buffer))
      .cursor
      .get[universe.v2.model.UniverseVersion]("version")

    decodedVersion match {
      case Xor.Right(version) => version
      case Xor.Left(failure) => throw PackageFileSchemaMismatch("index.json", failure)
    }
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

object DefaultUniverseClient {

  val TemporaryRedirect: Int = 307
  val PermanentRedirect: Int = 308

  val SupportedMediaTypes: List[MediaType] =
    List(MediaTypes.UniverseV3Repository, MediaTypes.UniverseV2Repository)

  def parseContentType(header: Option[String]): TwitterTry[MediaType] = {
    TwitterTry(header.getOrElse(throw UnsupportedContentType(SupportedMediaTypes)))
      .flatMap(MediaTypeParser.parse)
      .handle {
        case MediaTypeParseError(_, _) => throw UnsupportedContentType(SupportedMediaTypes)
      }
  }

}

object UniverseClient {
  def apply(adminRouter: AdminRouter)(implicit statsReceiver: StatsReceiver = NullStatsReceiver): UniverseClient = {
    new DefaultUniverseClient(adminRouter)
  }
}
