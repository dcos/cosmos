package com.mesosphere.cosmos.circe

import com.google.common.io.CharStreams
import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.ConcurrentAccess
import com.mesosphere.cosmos.CosmosError
import com.mesosphere.cosmos.IncompleteUninstall
import com.mesosphere.cosmos.PackageFileMissing
import com.mesosphere.cosmos.RepositoryUriConnection
import com.mesosphere.cosmos.RepositoryUriSyntax
import com.mesosphere.cosmos.ServiceUnavailable
import com.mesosphere.cosmos.circe.Decoders.decode64
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeSubType
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.LocalPackage
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.storage
import com.mesosphere.cosmos.storage.v1.circe.Decoders._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.circe.Encoders._
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
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

class EncodersDecodersSpec extends FreeSpec with PropertyChecks with Matchers {
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
    ): Assertion = {
      val repo = PackageRepository("repo", Uri.parse("http://example.com"))
      val cause = "original failure message"
      val error = errorConstructor(repo, new Throwable(cause))

      val Right(roundTripError) = error.asJson.as[ErrorResponse]
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
    ): Assertion = {
      val encodedFields = error.getData.getOrElse(JsonObject.empty)
      throwableFieldNames.foreach(name => assert(!encodedFields.contains(name), name))
      assertResult(error.productArity - throwableFieldNames.size)(encodedFields.size)
    }
  }

  "Encoder for io.circe.Error" - {
    "DecodingFailure should specify path instead of cursor history" - {
      "when value is valid type but not acceptable" in {
        val Left(err: DecodingFailure) = loadAndDecode(
          "/com/mesosphere/cosmos/universe_invalid_packagingVersion_value.json"
        )

        val encoded = encodeCirceError(err)
        val c = encoded.hcursor
        val Right(typ) = c.downField("type").as[String]
        val Right(dataType) = c.downField("data").downField("type").as[String]
        val Right(reason) = c.downField("data").downField("reason").as[String]
        val Right(path) = c.downField("data").downField("path").as[String]
        assertResult("json_error")(typ)
        assertResult("decode")(dataType)
        assertResult("Expected one of [2.0, 3.0] for packaging version, but found [1.0]")(reason)
        assertResult(".packages[0].packagingVersion")(path)
      }

      "when ByteBuffer value is incorrect type" in {
        val Left(err: DecodingFailure) = loadAndDecode(
          "/com/mesosphere/cosmos/universe_invalid_byteBuffer.json"
        )

        val encoded = encodeCirceError(err)
        val c = encoded.hcursor
        val Right(typ) = c.downField("type").as[String]
        val Right(dataType) = c.downField("data").downField("type").as[String]
        val Right(reason) = c.downField("data").downField("reason").as[String]
        val Right(path) = c.downField("data").downField("path").as[String]
        assertResult("json_error")(typ)
        assertResult("decode")(dataType)
        assertResult(".packages[0].marathon.v2AppMustacheTemplate")(path)
        assertResult("Base64 string value expected")(reason)
      }

    }

    "ParsingFailure should" - {
      "explain where the parsing failure occurred" in {
        val Left(err: ParsingFailure) = loadAndDecode("/com/mesosphere/cosmos/repository/malformed.json")
        val encoded = encodeCirceError(err)
        val c = encoded.hcursor
        val Right(typ) = c.downField("type").as[String]
        val Right(dataType) = c.downField("data").downField("type").as[String]
        val Right(reason) = c.downField("data").downField("reason").as[String]
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
    err.asInstanceOf[Exception].asJson // up-cast the error so that the implicit matches; io.circe.Error is too specific
  }

  private[this] def loadAndDecode(resourceName: String): Either[Error, Repository] = {
    import com.mesosphere.universe.v3.circe.Decoders._
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) =>
        val jsonString = CharStreams.toString(new InputStreamReader(is))
        is.close()
        jawn.decode[Repository](jsonString)
      case _ =>
        throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
  }

  "Operation" - {

    "type field is correct" in {
      val v3Package = TestingPackages.MinimalV3ModelV3PackageDefinition
      val uninstall: storage.v1.model.Operation = storage.v1.model.Uninstall(v3Package)
      val expectedUninstallJson = Json.obj(
        "v3Package" -> v3Package.asJson,
        "type" -> "Uninstall".asJson
      )
      val actualUninstallJson = uninstall.asJson
      assertResult(expectedUninstallJson)(actualUninstallJson)
    }

    "Install => Json => Install" in {
      val install: storage.v1.model.Operation =
        storage.v1.model.Install(
          UUID.fromString("13c825fe-a8b8-46de-aa9b-61c848fb6522"),
          TestingPackages.MinimalV3ModelV3PackageDefinition
        )
      val installPrime =
        decode[storage.v1.model.Operation](install.asJson.noSpaces)
      assertResult(install)(installPrime)
    }

    "UniverseInstall => Json => UniverseInstall" in {
      val universeInstall: storage.v1.model.Operation =
        storage.v1.model.UniverseInstall(
          TestingPackages.MinimalV3ModelV3PackageDefinition
        )
      val universeInstallPrime =
        decode[storage.v1.model.Operation](universeInstall.asJson.noSpaces)
      assertResult(universeInstall)(universeInstallPrime)
    }

    "Uninstall => Json => Uninstall" in {
      val uninstall: storage.v1.model.Operation =
        storage.v1.model.Uninstall(TestingPackages.MinimalV3ModelV3PackageDefinition)
      val uninstallPrime =
        decode[storage.v1.model.Operation](uninstall.asJson.noSpaces)
      assertResult(uninstall)(uninstallPrime)
    }
  }

  "OperationFailure" in {
    val operationFailure =
      storage.v1.model.OperationFailure(
        storage.v1.model.Uninstall(TestingPackages.MinimalV3ModelV3PackageDefinition),
        ErrorResponse("foo", "bar")
      )
    val operationFailurePrime =
      decode[storage.v1.model.OperationFailure](operationFailure.asJson.noSpaces)
    assertResult(operationFailure)(operationFailurePrime)
  }

  "PendingOperation" in {
    val pendingOperation =
      storage.v1.model.PendingOperation(
        storage.v1.model.Uninstall(TestingPackages.MinimalV3ModelV3PackageDefinition),
        None
      )
    val pendingOperationPrime =
      decode[storage.v1.model.PendingOperation](pendingOperation.asJson.noSpaces)
    assertResult(pendingOperation)(pendingOperationPrime)
  }

  "OperationStatus" - {
    "type field is correct" in {
      val pending: storage.v1.model.OperationStatus = storage.v1.model.PendingStatus(
        storage.v1.model.Uninstall(TestingPackages.MinimalV3ModelV3PackageDefinition),
        None
      )
      val expectedJson =
        Json.obj(
          "operation" ->
            Json.obj(
              "v3Package" -> TestingPackages.MinimalV3ModelV3PackageDefinition.asJson,
              "type" -> "Uninstall".asJson
            ),
          "failure" -> Json.Null,
          "type" -> "PendingStatus".asJson)
      val actualJson = pending.asJson
      assertResult(expectedJson)(actualJson)
    }

    "PendingStatus => Json => PendingStatus" in {
      val pending: storage.v1.model.OperationStatus =
        storage.v1.model.PendingStatus(
          storage.v1.model.Uninstall(TestingPackages.MinimalV3ModelV3PackageDefinition),
          None
        )
      val pendingPrime =
        decode[storage.v1.model.OperationStatus](pending.asJson.noSpaces)
      assertResult(pending)(pendingPrime)
    }

    "FailedStatus => Json => FailedStatus" in {
      val failed: storage.v1.model.OperationStatus =
        storage.v1.model.FailedStatus(
          storage.v1.model.OperationFailure(
            storage.v1.model.Uninstall(TestingPackages.MinimalV3ModelV3PackageDefinition),
            ErrorResponse("foo", "bar")
          )
        )
      val failedPrime =
        decode[storage.v1.model.OperationStatus](failed.asJson.noSpaces)
      assertResult(failed)(failedPrime)
    }
  }

  "For all LocalPackage; LocalPackage => JSON => LocalPackage" in {
    forAll { (localPackage: LocalPackage) =>
      val string = localPackage.asJson.noSpaces
      decode[LocalPackage](string) shouldBe localPackage
    }
  }

  "Base64 Decoder" - {
    "basic example" in {
      val ls = List("hello", "world")
      val string = ls.asJson.noSpaces
      val string64 = Base64.getEncoder.encodeToString(string.getBytes(StandardCharsets.UTF_8))
      assertResult(ls)(decode64[List[String]](string64))
    }
  }

}
