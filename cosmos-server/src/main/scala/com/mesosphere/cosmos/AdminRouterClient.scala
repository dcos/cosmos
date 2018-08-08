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
    logger.info("posting custom request to " + uri + " with " + body.asJson + "with token " + session)
    val p = post(uri, body.asJson)
    p.headerMap.add("Content-Type", "application/vnd.dcos.package.install-request+json;charset=utf-8;version=v1")
    p.headerMap.add("Accept", "application/vnd.dcos.package.install-response+json;charset=utf-8;version=v2")
    logger.info(s"${p.headerMap}")
    logger.info(s"${p}")
    client(p)
  }

  def postCustomPackageUninstallRequest(
      managerId: AppId,
      body: UninstallRequest
    )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "package" / "uninstall"
    client(post(uri, body.asJson))
  }

  def postCustomServiceDescribeRequest(
    managerId: AppId,
    body: ServiceDescribeRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "describe"
    client(post(uri, body.asJson))
  }

  def postCustomServiceUpdateRequest(
    managerId: AppId,
    body: ServiceUpdateRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "update"
    client(post(uri, body.asJson))
  }


}
