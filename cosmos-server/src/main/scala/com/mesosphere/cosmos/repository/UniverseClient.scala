package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.BuildProperties
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.EndpointUriConnection
import com.mesosphere.cosmos.error.EndpointUriSyntax
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.IndexNotFound
import com.mesosphere.cosmos.error.PackageFileMissing
import com.mesosphere.cosmos.error.PackageFileNotJson
import com.mesosphere.cosmos.error.PackageFileSchemaMismatch
import com.mesosphere.cosmos.error.RepositoryUriConnection
import com.mesosphere.cosmos.error.RepositoryUriSyntax
import com.mesosphere.cosmos.error.UniverseClientHttpError
import com.mesosphere.cosmos.error.UnsupportedContentType
import com.mesosphere.cosmos.error.UnsupportedRepositoryVersion
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.error.ResultOps
import com.mesosphere.http.CompoundMediaType
import com.mesosphere.http.MediaType
import com.mesosphere.universe
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.bijection.UniverseConversions._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Fields
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn.parse
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import org.jboss.netty.handler.codec.http.HttpMethod
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.Failure
import scala.util.Left
import scala.util.Right
import scala.util.Success
import scala.util.{Try => ScalaTry}

trait UniverseClient {
  def apply(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession
  ): Future[universe.v4.model.Repository]
}

final class DefaultUniverseClient(
  adminRouter: AdminRouter
)(
  implicit statsReceiver: StatsReceiver = NullStatsReceiver
) extends UniverseClient {

  import DefaultUniverseClient._

  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private[this] val stats = statsReceiver.scope("repositoryFetcher")
  private[this] val fetchScope = stats.scope("fetch")

  private[this] val cosmosVersion = BuildProperties().cosmosVersion

  def apply(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession
  ): Future[universe.v4.model.Repository] = {
    adminRouter.getDcosVersion().flatMap { dcosVersion =>
      apply(repository, dcosVersion.version).respond {
        case Return(_) =>
          logger.info(
            s"Success while fetching Universe state from ($repository, ${dcosVersion.version})"
          )
        case Throw(error) =>
          logger.error(
            s"Error while fetching Universe state from ($repository, ${dcosVersion.version})",
            error
          )
      }
    }
  }

  private[repository] def apply(
    repository: rpc.v1.model.PackageRepository,
    dcosReleaseVersion: universe.v3.model.DcosReleaseVersion
  ): Future[universe.v4.model.Repository] = {
    fetchScope.counter("requestCount").incr()
    Stat.timeFuture(fetchScope.stat("histogram")) {
      val acceptedMediaTypes = CompoundMediaType(
        MediaTypes.UniverseV5Repository,
        MediaTypes.UniverseV4Repository,
        MediaTypes.UniverseV3Repository
      )

      HttpClient
        .fetch(
          repository.uri,
          Fields.Accept -> acceptedMediaTypes.show,
          Fields.AcceptEncoding -> "gzip",
          Fields.UserAgent -> s"cosmos/$cosmosVersion dcos/${dcosReleaseVersion.show}"
        ) { responseData =>
          decodeAndSortUniverse(
            responseData.contentType,
            responseData.contentStream,
            repository.uri
          )
        }(fetchScope)
        .handle { case cosmosException: CosmosException =>
          cosmosException.error match {
            case EndpointUriSyntax(_, message) =>
              throw cosmosException.copy(error = RepositoryUriSyntax(repository, message))
            case EndpointUriConnection(_, message) =>
              throw cosmosException.copy(error = RepositoryUriConnection(repository, message))
            case GenericHttpError(_, _, clientStatus, _) =>
              /* If we are unable to get the latest Universe we should not forward the status code
               * returned. We should instead return 500 to the client and include the actual error
               * in the message.
               */
              throw UniverseClientHttpError(
                repository,
                HttpMethod.GET,
                clientStatus,
                HttpResponseStatus.INTERNAL_SERVER_ERROR
              ).exception
            case e => throw e.exception
          }
        }
    }
  }

  private[this] def decodeAndSortUniverse(
    contentType: MediaType,
    bodyInputStream: InputStream,
    repositoryUri: Uri
  ): universe.v4.model.Repository = {

    def processAsV4Repository(version : String) : universe.v4.model.Repository = {
      val scope = fetchScope.scope("decode").scope(version)
      scope.counter("count").incr()
      Stat.time(scope.stat("histogram")) {
        decode[universe.v4.model.Repository](
          Source.fromInputStream(bodyInputStream).mkString
        ).getOrThrow
      }
    }

    // Decode the packages
    val repo = if (contentType.isCompatibleWith(MediaTypes.UniverseV5Repository)) {
      processAsV4Repository("v5")
    } else if (contentType.isCompatibleWith(MediaTypes.UniverseV4Repository)) {
      processAsV4Repository("v4")
    } else if (contentType.isCompatibleWith(MediaTypes.UniverseV3Repository)) {
      processAsV4Repository("v3")
    } else if (contentType.isCompatibleWith(MediaTypes.UniverseV2Repository)) {
      val v2Scope = fetchScope.scope("decode").scope("v2")
      v2Scope.counter("count").incr()
      Stat.time(v2Scope.stat("histogram")) {
        processUniverseV2(repositoryUri, bodyInputStream)
      }
    } else {
      throw UnsupportedContentType.forMediaType(SupportedMediaTypes, Some(contentType)).exception
    }

    // Sort the packages
    universe.v4.model.Repository(repo.packages.sorted.reverse)

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
    packages: Map[(String, Long), Map[String, (Path, Array[Byte])]]
  )

  private[this] def processUniverseV2(
    sourceUri: Uri,
    inputStream: InputStream
  ): universe.v4.model.Repository = {
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
      case Some(version) => throw UnsupportedRepositoryVersion(version).exception
      case _ => throw IndexNotFound(sourceUri).exception
    }

    val packageInfos = universeRepository.packages.mapValues(processPackageFiles)
    val packages = packageInfos.toList.map { case ((_, releaseVersion), packageInfo) =>
      buildV2Package(packageInfo, releaseVersion)
    }

    universe.v4.model.Repository(packages)
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
        val releaseVersion = releaseVersionString.toLong
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
            throw PackageFileSchemaMismatch(
              "config.json",
              DecodingFailure("Object", List())
            ).exception
          }
      },
      resource = packageFiles.get("resource.json").map { case (entryPath, buffer) =>
        parseAndVerify[universe.v2.model.Resource](entryPath, new String(buffer))
      }
    )
  }

  private[this] def buildV2Package(
    packageInfo: V2PackageInformation,
    releaseVersion: Long
  ): universe.v3.model.V2Package = {
    val details = packageInfo.packageDetails.getOrElse(
      throw PackageFileMissing("package.json").exception
    )
    val marathon = packageInfo.marathonMustache.getOrElse(
      throw PackageFileMissing("marathon.json.mustache").exception
    )

    universe.v3.model.V2Package(
      universe.v3.model.V2PackagingVersion,
      details.name,
      universe.v3.model.Version(details.version.toString),
      releaseVersion.as[ScalaTry[universe.v3.model.ReleaseVersion]].get,
      details.maintainer,
      details.description,
      universe.v3.model.Marathon(marathon),
      details.tags.map {
        tag =>
          // The format of the tag is enforced by the json schema for universe packagingVersion 2.0 and 3.0
          // unfortunately com.mesosphere.universe.v2.model.PackageDetails#tags is a list string due to the
          // more formal type not being defined. The likelihood of this failing is remote, especially when the
          // source is universe-server.
          universe.v3.model.Tag(tag)
      },
      details.selected,
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
      .hcursor
      .get[universe.v2.model.UniverseVersion]("version")

    decodedVersion match {
      case Right(version) => version
      case Left(failure) => throw PackageFileSchemaMismatch("index.json", failure).exception
    }
  }

  private[this] def parseAndVerify[A: Decoder](
    path: Path,
    content: String
  ): A = {
    parseJson(path, content).as[A] match {
      case Left(err) => throw PackageFileSchemaMismatch(path.toString, err).exception
      case Right(right) => right
    }
  }

  private[this] def parseJson(
    path: Path,
    content: String
  ): Json = {
    parse(content) match {
      case Left(err) => throw PackageFileNotJson(path.toString, err.message).exception
      case Right(right) => right
    }
  }
}

object DefaultUniverseClient {

  val TemporaryRedirect: Int = 307
  val PermanentRedirect: Int = 308

  val SupportedMediaTypes: List[MediaType] =
    List(MediaTypes.UniverseV4Repository, MediaTypes.UniverseV3Repository, MediaTypes.UniverseV2Repository)
}

object UniverseClient {
  def apply(adminRouter: AdminRouter)(implicit statsReceiver: StatsReceiver = NullStatsReceiver): UniverseClient = {
    new DefaultUniverseClient(adminRouter)
  }
}
