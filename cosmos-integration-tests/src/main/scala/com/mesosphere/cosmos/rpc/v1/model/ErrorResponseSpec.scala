package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.http.PackageRpcPath
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.jawn._
import org.scalatest.FreeSpec

final class ErrorResponseSpec extends FreeSpec {

  "An ErrorResponse should be returned as the body when a request can't be parsed" in {
    val request = HttpRequest.post(
      PackageRpcPath("install"),
      Map("invalid" -> true),
      MediaTypes.InstallRequest,
      MediaTypes.V1InstallResponse
    )
    val response = CosmosClient.submit(request)

    assertResult(Status.BadRequest)(response.status)
    assertResult(MediaTypes.ErrorResponse.show)(response.headerMap(Fields.ContentType))
    val Right(err) = decode[ErrorResponse](response.contentString)
    val msg = err.message
    assert(msg.contains("body"))
    assert(msg.contains("decode value"))
    assert(msg.contains("packageName"))
  }

}
