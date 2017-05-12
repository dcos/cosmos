package com.mesosphere.cosmos.handler

import cats.syntax.either._
import com.mesosphere.cosmos.http.CompoundMediaTypeParser
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepositoryAddRequest
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn._
import io.circe.syntax._
import org.scalatest.FreeSpec
import scala.language.implicitConversions
import scala.util.Right

class RequestErrorsSpec extends FreeSpec {

  "Cosmos Error Handling" - {
    "Applicative handling" - {
      "Present but invalid Accept, Present but invalid Content-Type, Present but invalid Request Body" in {
        val accept = CompoundMediaTypeParser.parse(Seq(
          MediaTypes.V1DescribeResponse.show + ";q=0.1",
          MediaTypes.V2DescribeResponse.show + ";q=0.9"
        ).mkString(",")).get()

        val body = PackageRepositoryAddRequest(
          "bad",
          "http://bad/repo",
          Some(0)
        ).asJson

        val request = HttpRequest.post(
          "package/install",
          body.noSpaces,
          Some(MediaTypes.DescribeRequest.show),
          Some(accept.show)
        )
        val response = CosmosClient.submit(request)

        assertResult(Status.BadRequest)(response.status)
        assertResult(MediaTypes.ErrorResponse.show)(response.headerMap(Fields.ContentType))
        val Right(obj: JsonObject) = parse(response.contentString).map(jsonToJsonObject)

        val expectedErrorMessage = "Multiple errors while processing request"
        assertResult("multiple_errors")(obj.str("type"))
        assertResult(expectedErrorMessage)(obj.str("message"))

        val errors = obj.obj("data").arr("errors")
        assertResult(3)(errors.size)

        val acceptError :: contentTypeError :: bodyError :: Nil = errors.map(jsonToJsonObject)

        // assert Accept header error
        assertResult("not_valid")(acceptError.str("type"))
        val expectedAcceptErrorMessage =
          "Item header 'Accept' deemed invalid by rule: Media type was " +
        "[application/vnd.dcos.package.describe-response+json;charset=utf-8;version=v1;q=0.1, " +
        "application/vnd.dcos.package.describe-response+json;charset=utf-8;version=v2;q=0.9] " +
        "but should be one of [application/vnd.dcos.package.install-response+json;charset=utf-8;" +
        "version=v2, application/vnd.dcos.package.install-response+json;charset=utf-8;version=v1]"
        assertResult(expectedAcceptErrorMessage)(acceptError.str("message"))

        // assert Content-Type header error
        assertResult("not_valid")(contentTypeError.str("type"))
        val expectedContentTypeErrorMessage =
          "Item header 'Content-Type' deemed invalid by rule: should match on of " +
        "application/vnd.dcos.package.install-request+json;charset=utf-8;version=v1"
        assertResult(expectedContentTypeErrorMessage)(contentTypeError.str("message"))

        // assert Request body error
        assertResult("not_parsed")(bodyError.str("type"))
        val expectedBodyErrorMessage =
          "Item 'body' unable to be parsed: Attempt to decode value on failed cursor: " +
        "El(DownField(packageName),false,false)"
        assertResult(expectedBodyErrorMessage)(bodyError.str("message"))
      }

      "Missing Accept, Missing Content-Type, Missing body" in {
        val request = HttpRequest.post(
          path = "package/install",
          body = "",
          contentType = None,
          accept = None
        )
        val response = CosmosClient.submit(request)

        assertResult(Status.BadRequest)(response.status)
        assertResult(MediaTypes.ErrorResponse.show)(response.headerMap(Fields.ContentType))
        val Right(obj: JsonObject) = parse(response.contentString).map(jsonToJsonObject)

        val expectedErrorMessage = "Multiple errors while processing request"
        assertResult("multiple_errors")(obj.str("type"))
        assertResult(expectedErrorMessage)(obj.str("message"))

        val errors = obj.obj("data").arr("errors")
        assertResult(3)(errors.size)

        val acceptError :: contentTypeError :: bodyError :: Nil = errors.map(jsonToJsonObject)

        assertResult("not_present")(acceptError.str("type"))
        val expectedAcceptErrorMessage = "Item header 'Accept' not present but required"
        assertResult(expectedAcceptErrorMessage)(acceptError.str("message"))
        assertResult("not_present")(contentTypeError.str("type"))
        val expectedContentTypeErrorMessage = "Item header 'Content-Type' not present but required"
        assertResult(expectedContentTypeErrorMessage)(contentTypeError.str("message"))
        assertResult("not_parsed")(bodyError.str("type"))
        val expectedBodyErrorMessage = "Item 'body' unable to be parsed: exhausted input"
        assertResult(expectedBodyErrorMessage)(bodyError.str("message"))
      }
    }
  }

  private[this] def jsonToJsonObject(json: Json): JsonObject = {
    json.asObject.get
  }

  private[this] implicit class Ops(jsonObject: JsonObject) {
    def str(s: String): String      = getOpt(s, { _.asString })
    def obj(s: String): JsonObject  = getOpt(s, { _.asObject })
    def arr(s: String): List[Json]  = getOpt(s, { _.asArray })
    private[this] def getOpt[A](prop: String, f: Json => Option[A]): A = {
      jsonObject(prop).flatMap(f) match {
        case Some(v) => v
        case None => fail(s"Failed to resolve property '$prop' in ${jsonObject.asJson.noSpaces}")
      }
    }
  }


}
