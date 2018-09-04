package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Await
import com.twitter.util.Future
import io.circe.syntax._
import org.jboss.netty.handler.codec.http.HttpMethod

class AdminRouterClient(
  adminRouterUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(adminRouterUri) {

  import AdminRouterClient._

  def getDcosVersion()(
    implicit session: RequestSession
  ): Future[DcosVersion] = {
    Future {
      cachedDcosVersion.getOrElse {
        synchronized {
          cachedDcosVersion.getOrElse {
            val uri = "dcos-metadata" / "dcos-version.json"
            Await.result {
              /*
               * We should NEVER use Await.result when composing futures; this is an exception:
               *  - Intentionally blocking across multiple Futures
               *  - Storing the result to a volatile var on first success of calling IO.
               */
              client(get(uri))
                .flatMap(decodeTo[DcosVersion](HttpMethod.GET, uri, _))
                .foreach(dcosVersion => cachedDcosVersion = Some(dcosVersion))
            }
          }
        }
      }
    }
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
     managerId: AppId,
     body: rpc.v1.model.InstallRequest
   )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "package" / "install"
    val p = post(uri, body.asJson)
    p.headerMap.set(Fields.ContentType, MediaTypes.InstallRequest.show)
    p.headerMap.set(Fields.Accept, MediaTypes.V2InstallResponse.show)
    client(p)
  }

  def postCustomPackageUninstall(
      managerId: AppId,
      body: rpc.v1.model.UninstallRequest
    )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "package" / "uninstall"
    val p = post(uri, body.asJson)
    p.headerMap.set(Fields.ContentType, MediaTypes.UninstallRequest.show)
    p.headerMap.set(Fields.Accept, MediaTypes.UninstallResponse.show)
    client(p)
  }

  def postCustomServiceDescribe(
    managerId: AppId,
    body: rpc.v1.model.ServiceDescribeRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "describe"
    val p = post(uri, body.asJson)
    p.headerMap.set("X-Forwarded-Proto", session.originInfo.urlScheme.toString)
    p.headerMap.set("X-Forwarded-Host", session.originInfo.host)
    p.headerMap.set("X-Forwarded-Port", session.originInfo.port.getOrElse(""))
    p.headerMap.set(Fields.ContentType, MediaTypes.ServiceDescribeRequest.show)
    p.headerMap.set(Fields.Accept, MediaTypes.ServiceDescribeResponse.show)
    client(p)
  }

  def postCustomServiceUpdate(
    managerId: AppId,
    body: rpc.v1.model.ServiceUpdateRequest
  )(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / managerId.toUri / "service" / "update"
    val p = post(uri, body.asJson)
    p.headerMap.set(Fields.ContentType, MediaTypes.ServiceUpdateRequest.show)
    p.headerMap.set(Fields.Accept, MediaTypes.ServiceUpdateResponse.show)
    client(p)
  }
}



object AdminRouterClient {
  @volatile private var cachedDcosVersion: Option[DcosVersion] = None
}
