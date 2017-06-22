package com.mesosphere.cosmos.circe

import com.google.common.io.CharStreams
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeSubType
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.Json
import io.circe.ParsingFailure
import io.circe.jawn
import io.circe.syntax._
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import org.scalatest.prop.TableDrivenPropertyChecks

class EncodersDecodersSpec
extends FreeSpec with PropertyChecks with Matchers with TableDrivenPropertyChecks {
  import Decoders._
  import Encoders._

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

        val invalidVersion = "1.0"
        val invalidPackagingVersionErrorString =
          TestingPackages.renderInvalidVersionMessage(invalidVersion)

        assertResult("json_error")(typ)
        assertResult("decode")(dataType)
        assertResult(invalidPackagingVersionErrorString)(reason)
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
        val Left(err: ParsingFailure) = loadAndDecode(
          "/com/mesosphere/cosmos/repository/malformed.json"
        )
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
    // up-cast the error so that the implicit matches; io.circe.Error is too specific
    err.asInstanceOf[Exception].asJson
  }

  private[this] def loadAndDecode(
    resourceName: String
  ): Either[Error, universe.v4.model.Repository] = {
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) =>
        val jsonString = CharStreams.toString(new InputStreamReader(is))
        is.close()
        jawn.decode[universe.v4.model.Repository](jsonString)
      case _ =>
        throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
  }

  "Base64 decoder" - {
    "basic example" in {
      val ls = List("hello", "world")
      val string = ls.asJson.noSpaces
      val string64 = Base64.getEncoder.encodeToString(string.getBytes(StandardCharsets.UTF_8))
      assertResult(ls)(decode64[List[String]](string64))
    }
  }

  "Base64 parser" - {
    "basic example" in {
      val ls = Json.fromValues(List("hello".asJson, "world".asJson))
      val string = ls.noSpaces
      val string64 = Base64.getEncoder.encodeToString(string.getBytes(StandardCharsets.UTF_8))
      assertResult(ls)(parse64(string64))
    }
  }
}
