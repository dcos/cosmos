package com.mesosphere.cosmos

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.Uri
import com.twitter.util.Future
import io.circe.Decoder

package object http {

  def servicePath(action: String, direct: Boolean): String = {
    if (direct) s"/service/$action" else s"/cosmos/service/$action"
  }

  def packagePath(action: String, direct: Boolean): String = {
    // The path is always /package/...
    s"/package/$action"
  }

  def serviceDescribe(
    appId: AppId
  )(
    implicit context: TestContext
  ): Future[CosmosResponse[rpc.v1.model.ServiceDescribeResponse]] = {
    submit[rpc.v1.model.ServiceDescribeResponse](
      HttpRequest.post(
        servicePath("describe", context.client.direct),
        rpc.v1.model.ServiceDescribeRequest(appId),
        rpc.MediaTypes.ServiceDescribeRequest,
        rpc.MediaTypes.ServiceDescribeResponse
      )
    )
  }

  def packageRepoList(
  )(
    implicit context: TestContext
  ): Future[CosmosResponse[rpc.v1.model.PackageRepositoryListResponse]] = {
    submit[rpc.v1.model.PackageRepositoryListResponse](
      HttpRequest.post(
        path = packagePath("repository/list", context.client.direct),
        body = rpc.v1.model.PackageRepositoryListRequest(),
        contentType = rpc.MediaTypes.PackageRepositoryListRequest,
        accept = rpc.MediaTypes.PackageRepositoryListResponse
      )
    )
  }

  def packageRepoDelete(
    name: Option[String],
    uri: Option[Uri]
  )(
    implicit context: TestContext
  ): Future[CosmosResponse[rpc.v1.model.PackageRepositoryDeleteResponse]] = {
    submit[rpc.v1.model.PackageRepositoryDeleteResponse](
      HttpRequest.post(
        path = packagePath("repository/delete", context.client.direct),
        body = rpc.v1.model.PackageRepositoryDeleteRequest(name, uri),
        contentType = rpc.MediaTypes.PackageRepositoryDeleteRequest,
        accept = rpc.MediaTypes.PackageRepositoryDeleteResponse
      )
    )
  }

  def packageRepoAdd(
    source: rpc.v1.model.PackageRepository,
    index: Option[Int]
  )(
    implicit context: TestContext
  ): Future[CosmosResponse[rpc.v1.model.PackageRepositoryAddResponse]] = {
    submit[rpc.v1.model.PackageRepositoryAddResponse](
      HttpRequest.post(
        path = packagePath("repository/add", context.client.direct),
        body = rpc.v1.model.PackageRepositoryAddRequest(source.name, source.uri, index),
        contentType = rpc.MediaTypes.PackageRepositoryAddRequest,
        accept = rpc.MediaTypes.PackageRepositoryAddResponse
      )
    )
  }

  private def submit[Resp: Decoder](
    request: HttpRequest
  )(
    implicit context: TestContext
  ): Future[CosmosResponse[Resp]] = {
    context.client.submit(request).map(CosmosResponse[Resp])
  }

}
