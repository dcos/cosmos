package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession

import com.mesosphere.cosmos.rpc.v1.model.{InstallRequest, ServiceDescribeRequest, ServiceUpdateRequest, UninstallRequest}
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpMethod
import io.circe.syntax._
import org.slf4j.Logger

class AdminRouterClient(
  adminRouterUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(adminRouterUri) {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  object Constants {
    val V1 = "v1"
    val V2 = "v2"

    val InstallContentTypeHeaderBase = "application/vnd.dcos.package.install-request+json;charset=utf-8;version="
    val UninstallContentTypeHeaderBase = "application/vnd.dcos.package.uninstall-request+json;charset=utf-8;version="
    val ServiceUpdateContentTypeHeaderBase = "application/vnd.dcos.service.update-request+json;charset=utf-8;version="
    val ServiceDescribeContentTypeBase = "application/vnd.dcos.service.describe-request+json;charset=utf-8;version="

    val InstallAcceptHeaderBase = "application/vnd.dcos.package.install-response+json;charset=utf-8;version="
    val UninstallAcceptHeaderBase = "application/vnd.dcos.package.uninstall-response+json;charset=utf-8;version="
    val ServiceUpdateAcceptHeaderBase = "application/vnd.dcos.service.update-response+json;charset=utf-8;version="
    val ServiceDescribeAcceptHeaderBase = "application/vnd.dcos.service.describe-response+json;charset=utf-8;version="

    val InstallContentTypeHeaderV1 = InstallContentTypeHeaderBase + V1
    val UninstallContentTypeHeaderV1 = UninstallContentTypeHeaderBase + V1
    val ServiceUpdateContentTypeHeaderV1 = ServiceUpdateContentTypeHeaderBase + V1
    val ServiceDescribeContentTypeV1 = ServiceDescribeContentTypeBase + V1

    val InstallAcceptHeaderV2 = InstallAcceptHeaderBase + V2
    val UninstallAcceptHeaderV1 = UninstallAcceptHeaderBase + V1
    val ServiceUpdateAcceptHeaderV1 = ServiceUpdateAcceptHeaderBase + V1
    val ServiceDescribeAcceptHeaderV1 = ServiceDescribeAcceptHeaderBase + V1

    val ContentType = "Content-Type"
    val Accept = "Accept"
  }

  def getDcosVersion()(implicit session: RequestSession): Future[DcosVersion] = {
    val uri = "dcos-metadata" / "dcos-version.json"
    client(get(uri)).flatMap(decodeTo[DcosVersion](HttpMethod.GET, uri, _))
  }

  def getSdkServiceFrameworkIds(
    service: AppId,
    apiVersion: String
  )(implicit session: RequestSession): Future[List[String]] = {
    val uri = "service" / service.toUri / apiVersion / "state" / "frameworkId"
    client(get(uri)).flatMap(decodeTo[List[String]](HttpMethod.GET, uri, _))
  }

  def getSdkServicePlanStatus(
    service: AppId,
    apiVersion: String,
    plan: String
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / service.toUri / apiVersion / "plans" / plan
    client(get(uri))
  }

  def postCustomPackageInstallRequest(
     managerId: AppId,
     body: InstallRequest
   )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "package" / "install"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, Constants.InstallContentTypeHeaderV1)
    p.headerMap.set(Constants.Accept, Constants.InstallAcceptHeaderV2)
    client(p)
  }

  def postCustomPackageUninstallRequest(
      managerId: AppId,
      body: UninstallRequest
    )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "package" / "uninstall"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, Constants.UninstallContentTypeHeaderV1)
    p.headerMap.set(Constants.Accept, Constants.UninstallAcceptHeaderV1)
    client(p)
  }

  def postCustomServiceDescribeRequest(
    managerId: AppId,
    body: ServiceDescribeRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "describe"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, Constants.ServiceDescribeContentTypeV1)
    p.headerMap.set(Constants.Accept, Constants.ServiceDescribeAcceptHeaderV1)
    client(p)
  }

  def postCustomServiceUpdateRequest(
    managerId: AppId,
    body: ServiceUpdateRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "update"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, Constants.ServiceUpdateContentTypeHeaderV1)
    p.headerMap.set(Constants.Accept, Constants.ServiceUpdateAcceptHeaderV1)
    client(p)
  }


}
