package com.mesosphere.cosmos.circe

import cats.data.Ior
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.finch.{IncompatibleAcceptHeader, RequestError}
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model._
import com.twitter.finagle.http.Status
import io.circe.HistoryOp.opsToPath
import io.circe.generic.encoding.DerivedObjectEncoder
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.{DecodingFailure, Encoder, JsonObject, ParsingFailure}
import io.finch.Error
import org.jboss.netty.handler.codec.http.HttpMethod
import shapeless._
import shapeless.labelled.FieldType

/*
  Copied from circe v0.3.0
  This implicits contained in this trait are no longer present in circe v0.5.0 in favor of a macro.
  However, we need these rather than the macro so that
  `com.mesosphere.cosmos.circe.Encoders.dropThrowableFromEncodedObjects` works
 */
sealed trait LowPriorityImplicits {
  // https://github.com/travisbrown/circe/blob/v0.3.0/generic/shared/src/main/scala/io/circe/generic/encoding/DerivedObjectEncoder.scala#L10-L13
  implicit final val encodeHNil: DerivedObjectEncoder[HNil] = new DerivedObjectEncoder[HNil] {
    final def encodeObject(a: HNil): JsonObject = JsonObject.empty
  }

  // https://github.com/travisbrown/circe/blob/v0.3.0/generic/shared/src/main/scala/io/circe/generic/encoding/DerivedObjectEncoder.scala#L36-L46
  implicit final def encodeLabelledHList[K <: Symbol, H, T <: HList](implicit
    key: Witness.Aux[K],
    encodeHead: Lazy[Encoder[H]],
    encodeTail: Lazy[DerivedObjectEncoder[T]]
  ): DerivedObjectEncoder[FieldType[K, H] :: T] =
  new DerivedObjectEncoder[FieldType[K, H] :: T] {
    final def encodeObject(a: FieldType[K, H] :: T): JsonObject = a match {
      case h :: t =>
        (key.value.name -> encodeHead.value(h)) +: encodeTail.value.encodeObject(t)
    }
  }
}

object Encoders extends LowPriorityImplicits {

  implicit val encodeZooKeeperStorageEnvelope: Encoder[ZooKeeperStorageEnvelope] =
    deriveEncoder[ZooKeeperStorageEnvelope]

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

  /* This method skips all fields of type Throwable when rendering an object as JSON.
   *
   * Here's a rough analogy of how it works. Let's say you have some way of converting any case
   * class into a `List[(String, Any)]` of field names and values in order. You could pretty easily
   * serialize that `List` into JSON, assuming you have some "magic" encoding function
   * `encodeValue: Any => Json`:
   *
   * def encodeFields(fields: List[(String, Any)]): JsonObject = {
   *   fields match {
   *     case (key, value) :: xs => (key, encodeValue(value)) +: encodeFields(xs)
   *     case Nil                => JsonObject.empty
   *   }
   * }
   *
   * If you don't want to include values of type `Throwable`, you could update your function:
   *
   * def encodeNonThrowableFields(fields: List[(String, Any)]): JsonObject = {
   *   fields match {
   *     case (key, value: Throwable) :: xs => encodeFields(xs)
   *     case (key, value) :: xs            => (key, encodeValue(value)) +: encodeFields(xs)
   *     case Nil                           => JsonObject.empty
   *   }
   * }
   *
   * You can think of the method below as defining the first clause of that pattern match. Circe
   * uses the Shapeless library to convert a case class value into an HList, which corresponds to
   * the `List[(String, Any)]` in the analogy, except that it's type-safe. More precisely,
   * `FieldType[K, H] :: T` in the code is the equivalent of `case (key, value: Throwable) :: xs`.
   * See the `encodeLabelledHList` and `encodeHNil` in Circe's `DerivedObjectEncoder` for the other
   * two clauses of the "pattern match".
   *
   * Why would we want to skip `Throwable`s? Mostly because we don't control what is in the error
   * message, which could contain information that we don't want to surface to users in ordinary
   * error responses. It's better to make a conscious decision to include error data when needed.
   */
  implicit def dropThrowableFromEncodedObjects[K <: Symbol, H <: Throwable, T <: HList](implicit
    encodeTail: Lazy[DerivedObjectEncoder[T]]
  ): DerivedObjectEncoder[FieldType[K, H] :: T] =
    new DerivedObjectEncoder[FieldType[K, H] :: T] {
      def encodeObject(a: FieldType[K, H] :: T): JsonObject = {
        encodeTail.value.encodeObject(a.tail)
      }
    }

  implicit val encodeCosmosError: Encoder[CosmosError] = deriveEncoder[CosmosError]

  private[this] def exceptionErrorResponse(t: Throwable): ErrorResponse = t match {
    case cerr: io.circe.Error => circeErrorResponse(cerr)
    case Error.NotPresent(item) =>
      ErrorResponse("not_present", s"Item '${item.description}' not present but required")
    case Error.NotParsed(item, typ, cause) =>
      ErrorResponse("not_parsed", s"Item '${item.description}' unable to be parsed : '${cause.getMessage}'")
    case Error.NotValid(item, rule) =>
      ErrorResponse("not_valid", s"Item '${item.description}' deemed invalid by rule: '$rule'")
    case Error.RequestErrors(ts) =>
      val details = ts.map(exceptionErrorResponse).toList.asJson
      ErrorResponse(
        "multiple_errors",
        "Multiple errors while processing request",
        Some(JsonObject.singleton("errors", details))
      )
    case ce: RequestError =>
      ErrorResponse(ce.errType, msgForRequestError(ce), ce.getData)
    case t: Throwable =>
      ErrorResponse("unhandled_exception", t.getMessage)
  }

  private[this] def circeErrorResponse(cerr: io.circe.Error): ErrorResponse = cerr match {
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

  private[this] def msgForRequestError(re: RequestError): String = re match {
    case ce: CosmosError => msgForCosmosError(ce)
    case IncompatibleAcceptHeader(available, specified) =>
      s"Item 'header 'Accept'' deemed invalid by rule: 'should match one of: ${available.map(_.show).mkString(", ")}'"
  }

  private[this] def msgForCosmosError(err: CosmosError): String = err match {
    case ConcurrentPackageUpdateDuringPublish() =>
      "A concurrent update on this package has been performed. Please try again."
    case PackageNotFound(packageName) =>
      s"Package [$packageName] not found"
    case VersionNotFound(packageName, com.mesosphere.universe.v3.model.PackageDefinition.Version(packageVersion)) =>
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
    case CirceError(cerr) => cerr.getMessage
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
            s"Unable to shutdown [$pkgName] service framework with name [$fwName] because there are multiple framework " +
            s"ids matching this name: [${ids.mkString(", ")}]"
        case None =>
          s"Uninstalled package [$pkgName]\n" +
            s"Unable to shutdown [$pkgName] service framework with name [$fwName] because there are multiple framework " +
            s"ids matching this name: [${ids.mkString(", ")}]"
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
    case RepositoryAddIndexOutOfBounds(attempted, max) =>
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
    case ServiceMarathonTemplateNotFound(name, PackageDefinition.Version(version)) =>
      s"Package: [$name] version: [$version] does not have a Marathon template defined and can not be rendered"
     case EnvelopeError(msg) => msg
  }

}
