package com.mesosphere.cosmos.circe

import cats.data.Xor
import com.google.common.io.CharStreams
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeSubType
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.storage.installqueue._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.model.Repository
import com.netaporter.uri.Uri
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json
import io.circe.JsonObject
import io.circe.ParsingFailure
import io.circe.jawn
import io.circe.syntax._
import java.io.InputStreamReader
import org.scalatest.FreeSpec

class EncodersDecodersSpec extends FreeSpec {
  import Encoders._
  import Decoders._

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

    "IncompleteKill" in {
      val error = IncompleteKill(packageName = "spark", causedBy = throwable)
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
    val is = this.getClass.getResourceAsStream(resourceName)
    if (is == null) {
      throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
    val jsonString = CharStreams.toString(new InputStreamReader(is))
    is.close()
    jawn.decode[Repository](jsonString)
  }

  "Operation" - {

    "type field is correct" in {
      val uninstall: Operation =
        Uninstall(None)
      val expectedUninstallJson =
        Json.obj(
          "packageDefinition" -> Json.Null,
          "type" -> "Uninstall".asJson)
      val actualUninstallJson = uninstall.asJson
      assertResult(expectedUninstallJson)(actualUninstallJson)
    }

    "Install => Json => Install" in {
      val install: Operation =
        Install(
          Uri.parse("https://travisbrown.github.io/circe/"),
          TestingPackages.MinimalV3ModelV3PackageDefinition
        )
      val installPrime =
        decode[Install](install.asJson.noSpaces)
      assertResult(install)(installPrime)
    }

    "UniverseInstall => Json => UniverseInstall" in {
      val universeInstall: Operation =
        UniverseInstall(
          TestingPackages.MinimalV3ModelV3PackageDefinition
        )
      val universeInstallPrime =
        decode[UniverseInstall](universeInstall.asJson.noSpaces)
      assertResult(universeInstall)(universeInstallPrime)
    }

    "Uninstall => Json => Uninstall" in {
      val uninstall: Operation =
        Uninstall(None)
      val uninstallPrime =
        decode[Uninstall](uninstall.asJson.noSpaces)
      assertResult(uninstall)(uninstallPrime)
    }
  }

  "OperationFailure" in {
    val operationFailure =
      OperationFailure(Uninstall(None), ErrorResponse("foo", "bar"))
    val operationFailurePrime =
      decode[OperationFailure](operationFailure.asJson.noSpaces)
    assertResult(operationFailure)(operationFailurePrime)
  }

  "PendingOperation" in {
    val pendingOperation =
      PendingOperation(
        PackageCoordinate("foo", PackageDefinition.Version("2")),
        Uninstall(None),
        None
      )
    val pendingOperationPrime =
      decode[PendingOperation](pendingOperation.asJson.noSpaces)
    assertResult(pendingOperation)(pendingOperationPrime)
  }

  "OperationStatus" in {
    val operationStatus = OperationStatus(None, None)
    val operationStatusPrime =
      decode[OperationStatus](operationStatus.asJson.noSpaces)
    assertResult(operationStatus)(operationStatusPrime)
  }
}
