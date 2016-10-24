package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.google.common.io.CharStreams
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.http.{MediaType, MediaTypeSubType}
import com.mesosphere.cosmos.rpc.v1.model.{ErrorResponse, PackageRepository}
import com.mesosphere.universe.v3.model.Repository
import com.netaporter.uri.Uri
import io.circe.syntax._
import io.circe.jawn._
import io.circe.{DecodingFailure, Error, Json, JsonObject, ParsingFailure}
import org.scalatest.FreeSpec

import java.io.InputStreamReader

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
      assertThrowableDropped(PackageFileMissing(packageName = "kafka", cause = Some(throwable)), "cause")
    }

    "CirceError" in {
      assertThrowableDropped(CirceError(circeError = ParsingFailure("failed", throwable)), "cerr")
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

  "Encoder for io.circe.Error" - {
    "DecodingFailure should specify path instead of cursor history" - {
      "when value is valid type but not acceptable" in {
        val Xor.Left(err: DecodingFailure) = loadAndDecode(
          "/com/mesosphere/cosmos/universe_invalid_packagingVersion_value.json"
        )

        val encoded = encodeCirceError(err)
        val c = encoded.hcursor
        val Xor.Right(typ) = c.downField("type").as[String]
        val Xor.Right(dataType) = c.downField("data").downField("type").as[String]
        val Xor.Right(reason) = c.downField("data").downField("reason").as[String]
        val Xor.Right(path) = c.downField("data").downField("path").as[String]
        assertResult("json_error")(typ)
        assertResult("decode")(dataType)
        assertResult("Expected one of [2.0, 3.0] for packaging version, but found [1.0]")(reason)
        assertResult(".packages[0].packagingVersion")(path)
      }

      "when ByteBuffer value is incorrect type" in {
        val Xor.Left(err: DecodingFailure) = loadAndDecode(
          "/com/mesosphere/cosmos/universe_invalid_byteBuffer.json"
        )

        val encoded = encodeCirceError(err)
        val c = encoded.hcursor
        val Xor.Right(typ) = c.downField("type").as[String]
        val Xor.Right(dataType) = c.downField("data").downField("type").as[String]
        val Xor.Right(reason) = c.downField("data").downField("reason").as[String]
        val Xor.Right(path) = c.downField("data").downField("path").as[String]
        assertResult("json_error")(typ)
        assertResult("decode")(dataType)
        assertResult(".packages[0].marathon.v2AppMustacheTemplate")(path)
        assertResult("Base64 string value expected")(reason)
      }

    }

    "ParsingFailure should" - {
      "explain where the parsing failure occurred" in {
        val Xor.Left(err: ParsingFailure) = loadAndDecode("/com/mesosphere/cosmos/repository/malformed.json")
        val encoded = encodeCirceError(err)
        val c = encoded.hcursor
        val Xor.Right(typ) = c.downField("type").as[String]
        val Xor.Right(dataType) = c.downField("data").downField("type").as[String]
        val Xor.Right(reason) = c.downField("data").downField("reason").as[String]
        assertResult("json_error")(typ)
        assertResult("parse")(dataType)
        assertResult("expected \" got ] (line 1, column 2)")(reason)
      }
    }
  }

  "A MediaType should use show for encoding" in {
    val subType = MediaTypeSubType("vnd.dcos.custom-request", Some("json"))
    val mediaType = MediaType("application", subType)
    assertResult(Json.fromString("application/vnd.dcos.custom-request+json"))(mediaType.asJson)
  }

  private[this] def encodeCirceError(err: io.circe.Error): Json = {
    import com.mesosphere.cosmos.circe.Encoders._
    err.asInstanceOf[Exception].asJson // up-cast the error so that the implicit matches; io.circe.Error is too specific
  }

  private[this] def loadAndDecode(resourceName: String): Xor[Error, Repository] = {
    import com.mesosphere.universe.v3.circe.Decoders._
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) =>
        val jsonString = CharStreams.toString(new InputStreamReader(is))
        is.close()
        decode[Repository](jsonString)
      case _ =>
        throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
  }

}
