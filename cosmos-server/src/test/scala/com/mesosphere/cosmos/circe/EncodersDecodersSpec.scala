package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.netaporter.uri.Uri
import io.circe.syntax._
import io.circe.{JsonObject, ParsingFailure}
import org.scalatest.FreeSpec

class EncodersDecodersSpec extends FreeSpec {
  import Decoders._
  import Encoders._

  "CosmosError" - {
    "RepositoryUriSyntax" in {
      assertRoundTrip("RepositoryUriSyntax", RepositoryUriSyntax.apply)
    }


    "RepositoryUriConnection" in {
      assertRoundTrip("RepositoryUriConnection", RepositoryUriConnection.apply)
    }

    def assertRoundTrip(
      errorType: String,
      errorConstructor: (PackageRepository, Throwable) => Exception
    ): Unit = {
      val repo = PackageRepository("repo", Uri.parse("http://example.com"))
      val cause = "original failure message"
      val error = errorConstructor(repo, new Throwable(cause))

      val Xor.Right(roundTripError) = error.asJson.as[ErrorResponse]
      assertResult(errorType)(roundTripError.`type`)
      assertResult(Some(JsonObject.singleton("cause", cause.asJson)))(roundTripError.data)
    }
  }

  "Throwable fields are dropped from encoded objects" - {
    val throwable = new RuntimeException("BOOM!")

    "PackageFileMissing" in {
      assertThrowableDropped(PackageFileMissing(packageName = "kafka", cause = throwable), "cause")
    }

    "CirceError" in {
      assertThrowableDropped(CirceError(cerr = ParsingFailure("failed", throwable)), "cerr")
    }

    "ServiceUnavailable" in {
      val error = ServiceUnavailable(serviceName = "mesos", causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    "IncompleteUninstall" in {
      val error = IncompleteUninstall(packageName = "spark", causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    "ConcurrentAccess" in {
      assertThrowableDropped(ConcurrentAccess(causedBy = throwable), "causedBy")
    }

    "RepositoryUriSyntax" in {
      val repo = PackageRepository("Universe", Uri.parse("universe/repo"))
      val error = RepositoryUriSyntax(repository = repo, causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    "RepositoryUriConnection" in {
      val repo = PackageRepository("Universe", Uri.parse("universe/repo"))
      val error = RepositoryUriConnection(repository = repo, causedBy = throwable)
      assertThrowableDropped(error, "causedBy")
    }

    def assertThrowableDropped[A <: CosmosError with Product](
      error: A,
      throwableFieldNames: String*
    ): Unit = {
      val encodedFields = error.getData.getOrElse(JsonObject.empty)
      throwableFieldNames.foreach(name => assert(!encodedFields.contains(name), name))
      assertResult(error.productArity - throwableFieldNames.size)(encodedFields.size)
    }
  }

}
