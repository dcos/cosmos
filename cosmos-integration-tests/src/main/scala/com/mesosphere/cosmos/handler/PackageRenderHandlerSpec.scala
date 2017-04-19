package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.RenderRequest
import com.mesosphere.cosmos.rpc.v1.model.RenderResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn._
import io.circe.syntax._
import org.scalatest.FreeSpec
import scala.util.Right

class PackageRenderHandlerSpec extends FreeSpec {

  import PackageRenderHandlerSpec._

  "PackageRenderHandler should" - {

    "succeed when attempting to render a service with a marathon template" in {
      val expectedBody = RenderResponse(JsonObject.fromIterable(List(
        "id" -> "helloworld".asJson,
        "cpus" -> 1.0.asJson,
        "mem" -> 512.asJson,
        "instances" -> 1.asJson,
        "cmd" -> "python3 -m http.server 8080".asJson,
        "container" -> Json.fromFields(List(
          "type" -> "DOCKER".asJson,
          "docker" -> Json.fromFields(List(
            "image" -> "python:3".asJson,
            "network" -> "HOST".asJson
          ))
        )),
        "labels" -> Json.fromFields(List(
          "DCOS_PACKAGE_SOURCE" ->
            "https://downloads.mesosphere.com/universe/helloworld.zip".asJson,
          "DCOS_PACKAGE_METADATA" ->
            ("eyJwYWNrYWdpbmdWZXJzaW9uIjoiMi4wIiwibmFtZSI6ImhlbGxvd29ybGQiLCJ2ZXJzaW9uIjoiMC4xLj" +
             "AiLCJtYWludGFpbmVyIjoic3VwcG9ydEBtZXNvc3BoZXJlLmlvIiwiZGVzY3JpcHRpb24iOiJFeGFtcGxl" +
             "IERDT1MgYXBwbGljYXRpb24gcGFja2FnZSIsInRhZ3MiOlsibWVzb3NwaGVyZSIsImV4YW1wbGUiLCJzdW" +
             "Jjb21tYW5kIl0sInNlbGVjdGVkIjpmYWxzZSwid2Vic2l0ZSI6Imh0dHBzOi8vZ2l0aHViLmNvbS9tZXNv" +
             "c3BoZXJlL2Rjb3MtaGVsbG93b3JsZCIsImZyYW1ld29yayI6ZmFsc2UsInByZUluc3RhbGxOb3RlcyI6Ik" +
             "Egc2FtcGxlIHByZS1pbnN0YWxsYXRpb24gbWVzc2FnZSIsInBvc3RJbnN0YWxsTm90ZXMiOiJBIHNhbXBs" +
             "ZSBwb3N0LWluc3RhbGxhdGlvbiBtZXNzYWdlIn0=").asJson,
          "DCOS_PACKAGE_OPTIONS" -> "e30=".asJson,
          "DCOS_PACKAGE_VERSION" -> "0.1.0".asJson,
          "DCOS_PACKAGE_NAME" -> "helloworld".asJson
        ))
      )))
      val renderRequest = RenderRequest("helloworld", Some(PackageDetailsVersion("0.1.0")))

      val response = packageRender(renderRequest)

      assertResult(Status.Ok)(response.status)
      assertResult(MediaTypes.RenderResponse.show)(response.contentType.get)
      val Right(actualBody) = decode[RenderResponse](response.contentString)
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

      val renderRequest = RenderRequest("enterprise-security-cli")

      val response = packageRender(renderRequest)

      assertResult(Status.BadRequest)(response.status)
      assertResult(MediaTypes.ErrorResponse.show)(response.contentType.get)
      val Right(actualBody) = decode[ErrorResponse](response.contentString)
      assertResult(expectedBody)(actualBody)
    }

  }
}

object PackageRenderHandlerSpec {

  def packageRender(renderRequest: RenderRequest): Response = {
    val request = CosmosRequests.packageRender(renderRequest)
    CosmosClient.submit(request)
  }

}
