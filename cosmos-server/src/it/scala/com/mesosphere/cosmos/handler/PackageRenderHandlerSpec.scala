package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.ErrorResponse
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.{RenderRequest, RenderResponse}
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import io.circe.{Json, JsonObject}
import io.circe.jawn._
import io.circe.syntax._
import org.scalatest.FreeSpec

class PackageRenderHandlerSpec extends FreeSpec {
  import CosmosIntegrationTestClient._

  "PackageRenderHandler should" - {

    "succeed when attempting to render a service with a marathon template" in {
      val expectedBody = RenderResponse(Json.fromFields(Seq(
        "id" -> "helloworld".asJson,
        "cpus" -> 1.0.asJson,
        "mem" -> 512.asJson,
        "instances" -> 1.asJson,
        "cmd" -> "python3 -m http.server 8080".asJson,
        "container" -> Json.fromFields(Seq(
          "type" -> "DOCKER".asJson,
          "docker" -> Json.fromFields(Seq(
            "image" -> "python:3".asJson,
            "network" -> "HOST".asJson
          ))
        )),
        "labels" -> Json.fromFields(Seq(
          "DCOS_PACKAGE_RELEASE" -> "0".asJson,
          "DCOS_PACKAGE_SOURCE" -> "https://downloads.mesosphere.com/universe/helloworld.zip".asJson,
          "DCOS_PACKAGE_COMMAND" -> "eyJwaXAiOlsiZGNvczwxLjAiLCJnaXQraHR0cHM6Ly9naXRodWIuY29tL21lc29zcGhlcmUvZGNvcy1oZWxsb3dvcmxkLmdpdCNkY29zLWhlbGxvd29ybGQ9MC4xLjAiXX0=".asJson,
          "DCOS_PACKAGE_METADATA" -> "eyJwYWNrYWdpbmdWZXJzaW9uIjoiMi4wIiwibmFtZSI6ImhlbGxvd29ybGQiLCJ2ZXJzaW9uIjoiMC4xLjAiLCJtYWludGFpbmVyIjoic3VwcG9ydEBtZXNvc3BoZXJlLmlvIiwiZGVzY3JpcHRpb24iOiJFeGFtcGxlIERDT1MgYXBwbGljYXRpb24gcGFja2FnZSIsInRhZ3MiOlsibWVzb3NwaGVyZSIsImV4YW1wbGUiLCJzdWJjb21tYW5kIl0sInNlbGVjdGVkIjpmYWxzZSwid2Vic2l0ZSI6Imh0dHBzOi8vZ2l0aHViLmNvbS9tZXNvc3BoZXJlL2Rjb3MtaGVsbG93b3JsZCIsImZyYW1ld29yayI6ZmFsc2UsInByZUluc3RhbGxOb3RlcyI6IkEgc2FtcGxlIHByZS1pbnN0YWxsYXRpb24gbWVzc2FnZSIsInBvc3RJbnN0YWxsTm90ZXMiOiJBIHNhbXBsZSBwb3N0LWluc3RhbGxhdGlvbiBtZXNzYWdlIn0=".asJson,
          "DCOS_PACKAGE_REGISTRY_VERSION" -> "2.0".asJson,
          "DCOS_PACKAGE_VERSION" -> "0.1.0".asJson,
          "DCOS_PACKAGE_NAME" -> "helloworld".asJson,
          "DCOS_PACKAGE_IS_FRAMEWORK" -> "false".asJson
        ))
      )))
      val installRequest = RenderRequest("helloworld", Some(PackageDetailsVersion("0.1.0")))

      val request = CosmosClient.requestBuilder("package/render")
        .addHeader("Content-Type", MediaTypes.RenderRequest.show)
        .addHeader("Accept", MediaTypes.RenderResponse.show)
        .buildPost(Buf.Utf8(installRequest.asJson.noSpaces))

      val response = CosmosClient(request)

      assertResult(Status.Ok)(response.status)
      assertResult(MediaTypes.RenderResponse.show)(response.contentType.get)
      val Xor.Right(actualBody) = decode[RenderResponse](response.contentString)
      assertResult(expectedBody)(actualBody)
    }

    "error if attempting to render a service with no marathon template" in {
      val expectedBody = ErrorResponse(
        "ServiceMarathonTemplateNotFound",
        s"Package: [enterprise-security-cli] version: [0.8.0] does not have a " +
          "Marathon template defined and can not be rendered",
        Some(JsonObject.fromMap(Map(
          "packageName" -> "enterprise-security-cli".asJson,
          "packageVersion" -> "0.8.0".asJson
        )))
      )

      val installRequest = RenderRequest("enterprise-security-cli")

      val request = CosmosClient.requestBuilder("package/render")
        .addHeader("Content-Type", MediaTypes.RenderRequest.show)
        .addHeader("Accept", MediaTypes.RenderResponse.show)
        .buildPost(Buf.Utf8(installRequest.asJson.noSpaces))

      val response = CosmosClient(request)

      assertResult(Status.BadRequest)(response.status)
      assertResult(MediaTypes.ErrorResponse.show)(response.contentType.get)
      val Xor.Right(actualBody) = decode[ErrorResponse](response.contentString)
      assertResult(expectedBody)(actualBody)
    }

  }
}
