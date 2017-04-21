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
          "DCOS_PACKAGE_DEFINITION" -> (
            "eyJtZXRhZGF0YSI6eyJDb250ZW50LVR5cGUiOiJhcHBsaWNhdGlvbi92bmQuZGNvcy51bml2ZXJzZS5wYWN" +
            "rYWdlK2pzb247Y2hhcnNldD11dGYtODt2ZXJzaW9uPXYyIn0sImRhdGEiOiJleUp3WVdOcllXZHBibWRXWl" +
            "hKemFXOXVJam9pTWk0d0lpd2libUZ0WlNJNkltaGxiR3h2ZDI5eWJHUWlMQ0oyWlhKemFXOXVJam9pTUM0e" +
            "ExqQWlMQ0p5Wld4bFlYTmxWbVZ5YzJsdmJpSTZNQ3dpYldGcGJuUmhhVzVsY2lJNkluTjFjSEJ2Y25SQWJX" +
            "VnpiM053YUdWeVpTNXBieUlzSW1SbGMyTnlhWEIwYVc5dUlqb2lSWGhoYlhCc1pTQkVRMDlUSUdGd2NHeHB" +
            "ZMkYwYVc5dUlIQmhZMnRoWjJVaUxDSnRZWEpoZEdodmJpSTZleUoyTWtGd2NFMTFjM1JoWTJobFZHVnRjR3" +
            "hoZEdVaU9pSmxkMjluU1VOS2NGcERTVFpKUTBwdldsZDRjMkl6WkhaamJYaHJTV2wzUzBsRFFXbFpNMEl4W" +
            "TNsSk5rbEVSWFZOUTNkTFNVTkJhV0pYVm5SSmFtOW5UbFJGZVV4QmIyZEpRMHB3WW01T01GbFhOV3BhV0Ux" +
            "cFQybEJlRXhCYjJkSlEwcHFZbGRSYVU5cFFXbGpTR3d3WVVjNWRVMTVRWFJpVTBKdlpFaFNkMHh1VG14amJ" +
            "scHNZMmxDTjJVelFuWmpibEk1WmxOSmMwTnBRV2RKYlU1MlltNVNhR0ZYTld4amFVazJTVWh6UzBsRFFXZE" +
            "pRMG93WlZoQ2JFbHFiMmRKYTFKUVVUQjBSbFZwU1hORGFVRm5TVU5CYVZwSE9XcGhNbFo1U1dwdloyVjNiM" +
            "mRKUTBGblNVTkJhV0ZYTVdoYU1sVnBUMmxCYVdOSWJEQmhSemwxVDJwTmFVeEJiMmRKUTBGblNVTkJhV0p0" +
            "VmpCa01qbDVZWGxKTmtsRFNrbFVNVTVWU1dkdlowbERRV2RtVVc5blNVZ3dTMlpSYnowaWZTd2lkR0ZuY3l" +
            "JNld5SnRaWE52YzNCb1pYSmxJaXdpWlhoaGJYQnNaU0lzSW5OMVltTnZiVzFoYm1RaVhTd2ljMlZzWldOMF" +
            "pXUWlPbVpoYkhObExDSnpZMjBpT201MWJHd3NJbmRsWW5OcGRHVWlPaUpvZEhSd2N6b3ZMMmRwZEdoMVlpN" +
            "WpiMjB2YldWemIzTndhR1Z5WlM5a1kyOXpMV2hsYkd4dmQyOXliR1FpTENKbWNtRnRaWGR2Y21zaU9tNTFi" +
            "R3dzSW5CeVpVbHVjM1JoYkd4T2IzUmxjeUk2SWtFZ2MyRnRjR3hsSUhCeVpTMXBibk4wWVd4c1lYUnBiMjR" +
            "nYldWemMyRm5aU0lzSW5CdmMzUkpibk4wWVd4c1RtOTBaWE1pT2lKQklITmhiWEJzWlNCd2IzTjBMV2x1Yz" +
            "NSaGJHeGhkR2x2YmlCdFpYTnpZV2RsSWl3aWNHOXpkRlZ1YVc1emRHRnNiRTV2ZEdWeklqcHVkV3hzTENKc" +
            "2FXTmxibk5sY3lJNmJuVnNiQ3dpY21WemIzVnlZMlVpT201MWJHd3NJbU52Ym1acFp5STZleUlrYzJOb1pX" +
            "MWhJam9pYUhSMGNEb3ZMMnB6YjI0dGMyTm9aVzFoTG05eVp5OXpZMmhsYldFaklpd2lkSGx3WlNJNkltOWl" +
            "hbVZqZENJc0luQnliM0JsY25ScFpYTWlPbnNpY0c5eWRDSTZleUowZVhCbElqb2lhVzUwWldkbGNpSXNJbV" +
            "JsWm1GMWJIUWlPamd3T0RCOWZTd2lZV1JrYVhScGIyNWhiRkJ5YjNCbGNuUnBaWE1pT21aaGJITmxmU3dpW" +
            "TI5dGJXRnVaQ0k2ZXlKd2FYQWlPbHNpWkdOdmN6d3hMakFpTENKbmFYUXJhSFIwY0hNNkx5OW5hWFJvZFdJ" +
            "dVkyOXRMMjFsYzI5emNHaGxjbVV2WkdOdmN5MW9aV3hzYjNkdmNteGtMbWRwZENOa1kyOXpMV2hsYkd4dmQ" +
            "yOXliR1E5TUM0eExqQWlYWDE5In0="
          ).asJson,
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

    "test that the new repo works" in {
      val renderRequest = RenderRequest("cosmos-test-bitbucket")
      val response = packageRender(renderRequest)
      assertResult(Status.Ok)(response.status)
    }

  }
}

object PackageRenderHandlerSpec {

  def packageRender(renderRequest: RenderRequest): Response = {
    val request = CosmosRequests.packageRender(renderRequest)
    CosmosClient.submit(request)
  }

}
