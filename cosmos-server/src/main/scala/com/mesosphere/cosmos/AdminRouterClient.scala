package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.adminrouter.circe.Decoders._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpMethod

class AdminRouterClient(
  adminRouterUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(adminRouterUri) {

  def getDcosVersion()(implicit session: RequestSession): Future[DcosVersion] = {
    val uri = "dcos-metadata" / "dcos-version.json"
    client(get(uri)).flatMap(decodeTo[DcosVersion](HttpMethod.GET, uri, _))
  }

  def getSdkServicePlanStatus(service: String, apiVersion: String, plan: String)(implicit session: RequestSession): Future[Response] = {
    val uri = "service" / service / apiVersion / "plans" / plan
    client(get(uri))
  }
}
