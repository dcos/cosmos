package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.{InstallRequest, UninstallRequest}
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpMethod
import io.circe.syntax._


class AdminRouterClient(
  adminRouterUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(adminRouterUri) {
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

  def postCustomPackageInstall(
     service: AppId,
     body: InstallRequest
   )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / service.toUri / "package" / "install"
    client(post(uri, body.asJson))
  }

  def postCustomPackageUninstall(
      service: AppId,
      body: UninstallRequest
    )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / service.toUri / "package" / "uninstall"
    client(post(uri, body.asJson))
  }
}
