package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Buf

object CosmosRequests {

  def packageAdd(packageData: Buf): HttpRequest = {
    HttpRequest.post(
      path = "package/add",
      body = packageData,
      contentType = universe.MediaTypes.PackageZip,
      accept = rpc.MediaTypes.AddResponse
    )
  }

  def packageAdd(requestBody: rpc.v1.model.UniverseAddRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/add",
      body = requestBody,
      contentType = rpc.MediaTypes.AddRequest,
      accept = rpc.MediaTypes.AddResponse
    )
  }

  def packageDescribeV1(describeRequest: rpc.v1.model.DescribeRequest): HttpRequest = {
    packageDescribe(describeRequest, accept = rpc.MediaTypes.V1DescribeResponse)
  }

  def packageDescribeV2(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): HttpRequest = {
    val oldVersion = packageVersion.as[Option[universe.v2.model.PackageDetailsVersion]]
    val describeRequest = rpc.v1.model.DescribeRequest(packageName, oldVersion)
    packageDescribeV2(describeRequest)
  }

  def packageDescribeV2(describeRequest: rpc.v1.model.DescribeRequest): HttpRequest = {
    packageDescribe(describeRequest, accept = rpc.MediaTypes.V2DescribeResponse)
  }

  def packageInstallV1(installRequest: rpc.v1.model.InstallRequest): HttpRequest = {
    packageInstall(installRequest, accept = rpc.MediaTypes.V1InstallResponse)
  }

  def packageInstallV2(installRequest: rpc.v1.model.InstallRequest): HttpRequest = {
    packageInstall(installRequest, accept = rpc.MediaTypes.V2InstallResponse)
  }

  def packageListVersions(listVersionsRequest: rpc.v1.model.ListVersionsRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/list-versions",
      body = listVersionsRequest,
      contentType = rpc.MediaTypes.ListVersionsRequest,
      accept = rpc.MediaTypes.ListVersionsResponse
    )
  }

  private def packageDescribe(
    describeRequest: rpc.v1.model.DescribeRequest,
    accept: MediaType
  ): HttpRequest = {
    HttpRequest.post(
      path = "package/describe",
      body = describeRequest,
      contentType = rpc.MediaTypes.DescribeRequest,
      accept = accept
    )
  }

  private def packageInstall(
    installRequest: rpc.v1.model.InstallRequest,
    accept: MediaType
  ): HttpRequest = {
    HttpRequest.post(
      path = "package/install",
      body = installRequest,
      contentType = rpc.MediaTypes.InstallRequest,
      accept = accept
    )
  }

}
