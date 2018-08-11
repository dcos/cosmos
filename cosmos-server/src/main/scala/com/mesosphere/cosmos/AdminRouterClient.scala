package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Await
import com.twitter.util.Future
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
              /**
               * We should NEVER use Await.result when composing futures; this is an exception:
               *  - Intentional blocking across multiple Futures
               *  - Storing the result to a mutable var on first success of calling IO.
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
}

object AdminRouterClient {
  @volatile private var cachedDcosVersion: Option[DcosVersion] = None
}
