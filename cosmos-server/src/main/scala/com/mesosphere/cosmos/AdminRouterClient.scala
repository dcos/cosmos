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
import com.mesosphere.cosmos.rpc.MediaTypes


class AdminRouterClient(
  adminRouterUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(adminRouterUri) {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  object Constants {
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
    p.headerMap.set(Constants.ContentType, MediaTypes.InstallRequest.show)
    p.headerMap.set(Constants.Accept, MediaTypes.V2InstallResponse.show)
    client(p)
  }

  def postCustomPackageUninstallRequest(
      managerId: AppId,
      body: UninstallRequest
    )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "package" / "uninstall"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, MediaTypes.UninstallRequest.show)
    p.headerMap.set(Constants.Accept, MediaTypes.UninstallResponse.show)
    client(p)
  }

  def postCustomServiceDescribeRequest(
    managerId: AppId,
    body: ServiceDescribeRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "describe"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, MediaTypes.ServiceDescribeRequest.show)
    p.headerMap.set(Constants.Accept, MediaTypes.ServiceDescribeResponse.show)
    client(p)
  }

  def postCustomServiceUpdateRequest(
    managerId: AppId,
    body: ServiceUpdateRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "update"
    val p = post(uri, body.asJson)
    p.headerMap.set(Constants.ContentType, MediaTypes.ServiceUpdateRequest.show)
    p.headerMap.set(Constants.Accept, MediaTypes.ServiceDescribeResponse.show)
    client(p)
  }


}
