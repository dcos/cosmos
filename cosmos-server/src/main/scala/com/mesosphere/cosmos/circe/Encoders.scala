package com.mesosphere.cosmos.circe

import cats.data.Ior
import com.mesosphere.cosmos.CosmosError
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.LocalPackage
import com.mesosphere.cosmos.storage.v1.model.FailedStatus
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.Operation
import com.mesosphere.cosmos.storage.v1.model.OperationFailure
import com.mesosphere.cosmos.storage.v1.model.OperationStatus
import com.mesosphere.cosmos.storage.v1.model.PendingOperation
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.mesosphere.cosmos.storage.v1.model.Uninstall
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HistoryOp.opsToPath
import io.circe.Json
import io.circe.JsonObject
import io.circe.ParsingFailure
import io.circe.generic.encoding.DerivedObjectEncoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.finch.Error
import io.finch.Errors
import org.jboss.netty.handler.codec.http.HttpMethod


object Encoders {

  implicit val encodeZooKeeperStorageEnvelope: Encoder[ZooKeeperStorageEnvelope] =
    deriveEncoder[ZooKeeperStorageEnvelope]

  implicit val encodeInstall: Encoder[Install] =
    deriveEncoder[Install]

  implicit val encodeUniverseInstall: Encoder[UniverseInstall] =
    deriveEncoder[UniverseInstall]

  implicit val encodeUninstall: Encoder[Uninstall] =
    deriveEncoder[Uninstall]

  implicit val encodeOperation: Encoder[Operation] =
    Encoder.instance { operation =>
      val (json: Json, subclass: String) = operation match {
        case i: Install => (i.asJson, i.getClass.getSimpleName)
        case ui: UniverseInstall => (ui.asJson, ui.getClass.getSimpleName)
        case u: Uninstall => (u.asJson, u.getClass.getSimpleName)
      }

      json.mapObject(_.add("type", subclass.asJson))
    }

  implicit val encodeOperationFailure: Encoder[OperationFailure] =
    deriveEncoder[OperationFailure]

  implicit val encodePendingOperation: Encoder[PendingOperation] =
    deriveEncoder[PendingOperation]

  implicit val encodePending: Encoder[PendingStatus] =
    deriveEncoder[PendingStatus]

  implicit val encodeFailedStatus: Encoder[FailedStatus] =
    deriveEncoder[FailedStatus]

  implicit val encodeOperationStatus: Encoder[OperationStatus] = {
    Encoder.instance { operationStatus =>
      val (json: Json, subclass: String) = operationStatus match {
        case pending: PendingStatus =>
          (pending.asJson, pending.getClass.getSimpleName)
        case failed: FailedStatus =>
          (failed.asJson, failed.getClass.getSimpleName)
      }
      json.mapObject(_.add("type", subclass.asJson))
    }
  }

  /* This encoder converts a LocalPackage into a JSON object. The total number of fields are
   * enumerated below.
   *
   * {
   *   "status": <String>,
   *   "metadata": <PackageDefinition>,
   *   "operation": <Operation>,
   *   "error": <ErrorResponse>,
   *   "packageCoordinate": <PackageCoordinate>
   * }
   *
   * The 'status' will always be set while the rest of the properties are optional.
   */
  implicit val encodeLocalPackage = new Encoder[LocalPackage] {
    final override def apply(value: LocalPackage): Json = {
      val dataField = value.metadata.fold(
        pc => ("packageCoordinate", pc.asJson),
        pkg => ("metadata", pkg.asJson)
      )

      Json.obj(
        "status" -> Json.fromString(value.getClass.getSimpleName),
        dataField,
        "error" -> value.error.asJson,
        "operation" -> value.operation.asJson
      )
    }
  }

  implicit val exceptionEncoder: Encoder[Exception] = {
    Encoder.instance { e => exceptionErrorResponse(e).asJson }
  }

  implicit val encodeStatus: Encoder[Status] = Encoder.encodeInt.contramap(_.code)
  implicit val encodeMediaType: Encoder[MediaType] = Encoder.encodeString.contramap(_.show)
  implicit val encodeHttpMethod: Encoder[HttpMethod] = Encoder.encodeString.contramap(_.getName)

  implicit def encodeIor[A, B](implicit
    encodeA: Encoder[A],
    encodeB: Encoder[B]
  ): Encoder[Ior[A, B]] = deriveEncoder[Ior[A, B]]

  def exceptionErrorResponse(t: Throwable): ErrorResponse = t match {
    case circeError: io.circe.Error => circeErrorResponse(circeError)
    case Error.NotPresent(item) =>
      ErrorResponse("not_present", s"Item ${item.description} not present but required")
    case Error.NotParsed(item, _, cause) =>
      ErrorResponse(
        "not_parsed",
        s"Item '${item.description}' unable to be parsed: ${cause.getMessage}"
      )
    case Error.NotValid(item, rule) =>
      ErrorResponse("not_valid", s"Item ${item.description} deemed invalid by rule: $rule")
    case Errors(ts) =>
      val details = ts.map(exceptionErrorResponse).toList.asJson
      ErrorResponse(
        "multiple_errors",
        "Multiple errors while processing request",
        Some(JsonObject.singleton("errors", details))
      )
    case ce: CosmosError =>
      ErrorResponse(ce.errType, msgForCosmosError(ce), ce.getData)
    case t: Throwable =>
      ErrorResponse("unhandled_exception", t.getMessage)
  }

  private[this] def circeErrorResponse(circeError: io.circe.Error): ErrorResponse = circeError match {
    case pf: ParsingFailure =>
      ErrorResponse(
        "json_error",
        s"Json parsing failure '${pf.message}'",
        data = Some(JsonObject.fromMap(Map(
          "type" -> "parse".asJson,
          "reason" -> pf.message.asJson
        )))
      )
    case df: DecodingFailure =>
      val path = opsToPath(df.history)
      ErrorResponse(
        "json_error",
        s"Json decoding failure '${df.message}' at: $path",
        data = Some(JsonObject.fromMap(Map(
          "type" -> "decode".asJson,
          "reason" -> df.message.asJson,
          "path" -> path.asJson
        )))
      )
  }

  // scalastyle:off cyclomatic.complexity method.length
  private[this] def msgForCosmosError(err: CosmosError): String = {
    import com.mesosphere.cosmos._

    err match {
      case PackageNotFound(packageName) =>
        s"Package [$packageName] not found"
      case VersionNotFound(
        packageName,
        com.mesosphere.universe.v3.model.PackageDefinition.Version(packageVersion)
      ) =>
        s"Version [$packageVersion] of package [$packageName] not found"
      case PackageFileMissing(fileName, _) =>
        s"Package file [$fileName] not found"
      case PackageFileNotJson(fileName, parseError) =>
        s"Package file [$fileName] is not JSON: $parseError"
      case UnableToParseMarathonAsJson(parseError) =>
        "Unable to parse filled-in Marathon template as JSON; there " +
        "may be an error in the package's Marathon template or default " +
        "configuration options, or in the installation request's options. " +
        s"Parsing error was: $parseError"
      case PackageFileSchemaMismatch(fileName, _) =>
        s"Package file [$fileName] does not match schema"
      case PackageAlreadyInstalled() =>
        "Package is already installed"
      case ServiceAlreadyStarted() =>
        "The DC/OS service has already been started"
      case MarathonBadResponse(marathonErr) => marathonErr.message
      case MarathonGenericError(marathonStatus) =>
        s"Received response status code ${marathonStatus.code} from Marathon"
      case MarathonBadGateway(marathonStatus) =>
        s"Received response status code ${marathonStatus.code} from Marathon"
      case IndexNotFound(repoUri) =>
        s"Index file missing for repo [$repoUri]"
      case MarathonAppDeleteError(appId) =>
        s"Error while deleting marathon app '$appId'"
      case MarathonAppNotFound(appId) =>
        s"Unable to locate service with marathon appId: '$appId'"
      case CirceError(circeError) => circeError.getMessage
      case MarathonTemplateMustBeJsonObject => "Rendered Marathon JSON must be a JSON object"
      case JsonSchemaMismatch(_) =>
        "Options JSON failed validation"
      case UnsupportedContentType(supported, actual) =>
        val acceptMsg = supported.map(_.show).mkString("[", ", ", "]")
        actual match {
          case Some(mt) =>
            s"Unsupported Content-Type: $mt Accept: $acceptMsg"
          case None =>
            s"Unspecified Content-Type Accept: $acceptMsg"
        }
      case UnsupportedContentEncoding(supported, actual) =>
        val acceptMsg = supported.mkString("[", ", ", "]")
        actual match {
          case Some(mt) =>
            s"Unsupported Content-Encoding: $mt Accept-Encoding: $acceptMsg"
          case None =>
            s"Unspecified Content-Encoding Accept-Encoding: $acceptMsg"
        }
      case GenericHttpError(method, uri, status) =>
        s"Unexpected down stream http error: ${method.getName} ${uri.toString} ${status.code}"
      case AmbiguousAppId(pkgName, appIds) =>
        s"Multiple apps named [$pkgName] are installed: [${appIds.mkString(", ")}]"
      case MultipleFrameworkIds(pkgName, pkgVersion, fwName, ids) =>
        pkgVersion match {
          case Some(ver) =>
            s"Uninstalled package [$pkgName] version [$ver]\n" +
            s"Unable to shutdown [$pkgName] service framework with name [$fwName] because there " +
            s"are multiple framework ids matching this name: [${ids.mkString(", ")}]"
          case None =>
            s"Uninstalled package [$pkgName]\n" +
            s"Unable to shutdown [$pkgName] service framework with name [$fwName] because there " +
            s"are multiple framework ids matching this name: [${ids.mkString(", ")}]"
        }
      case PackageNotInstalled(pkgName) =>
        s"Package [$pkgName] is not installed"
      case UninstallNonExistentAppForPackage(pkgName, appId) =>
        s"Package [$pkgName] with id [$appId] is not installed"

      case ServiceUnavailable(serviceName, _) =>
        s"Unable to complete request due to service [$serviceName] unavailability"
      case u: Unauthorized =>
        u.getMessage
      case f: Forbidden =>
        f.getMessage

      case IncompleteUninstall(packageName, _) =>
        s"Incomplete uninstall of package [$packageName] due to Mesos unavailability"

      case RepoNameOrUriMissing() =>
        s"Must specify either the name or URI of the repository"
      case ZooKeeperStorageError(msg) => msg
      case ConcurrentAccess(_) =>
        s"Retry operation. Operation didn't complete due to concurrent access."
      case RepositoryAlreadyPresent(nameOrUri) =>
        nameOrUri match {
          case Ior.Both(n, u) =>
            s"Repository name [$n] and URI [$u] are both already present in the list"
          case Ior.Left(n) => s"Repository name [$n] is already present in the list"
          case Ior.Right(u) => s"Repository URI [$u] is already present in the list"
        }
      case RepositoryAddIndexOutOfBounds(attempted, _) =>
        s"Index out of range: $attempted"
      case UnsupportedRepositoryVersion(version) => s"Repository version [$version] is not supported"
      case UnsupportedRepositoryUri(uri) => s"Repository URI [$uri] uses an unsupported scheme. " +
      "Only http and https are supported"
      case RepositoryUriSyntax(repository, _) =>
        s"URI for repository [${repository.name}] has invalid syntax: ${repository.uri}"
      case RepositoryUriConnection(repository, _) =>
        s"Could not access data at URI for repository [${repository.name}]: ${repository.uri}"
      case RepositoryNotPresent(nameOrUri) =>
        nameOrUri match {
          case Ior.Both(n, u) => s"Neither repository name [$n] nor URI [$u] are present in the list"
          case Ior.Left(n) => s"Repository name [$n] is not present in the list"
          case Ior.Right(u) => s"Repository URI [$u] is not present in the list"
        }
      case UnsupportedRedirect(supported, actual) =>
        val supportedMsg = supported.mkString("[", ", ", "]")
        actual match {
          case Some(act) =>
            s"Unsupported redirect scheme - supported: $supportedMsg actual: $act"
          case None =>
            s"Unsupported redirect scheme - supported: $supportedMsg"
        }
      case ConversionError(failure) => failure
      case ServiceMarathonTemplateNotFound(name, universe.v3.model.PackageDefinition.Version(version)) =>
        s"Package: [$name] version: [$version] does not have a Marathon template defined and can not be rendered"
      case EnvelopeError(msg) => msg
      case InstallQueueError(msg) => msg
      case NotImplemented(msg) => msg
      case OperationInProgress(coordinate) =>
        s"A change to package ${coordinate.name}-${coordinate.version} is already in progress"
    }
  }
  // scalastyle:on cyclomatic.complexity method.length
}
