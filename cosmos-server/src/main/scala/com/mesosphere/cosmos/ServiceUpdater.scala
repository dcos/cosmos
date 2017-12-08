package com.mesosphere.cosmos

import _root_.io.circe.JsonObject
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.util.Future

final class ServiceUpdater(adminRouter: AdminRouter) {
  def update(
    appId: AppId,
    renderedConfig: JsonObject
  )(implicit session: RequestSession): Future[String] = {
    adminRouter.update(appId, renderedConfig).map { response: Response =>
      response.status match {
        case Status.Ok =>
          parse(response.contentString).hcursor.get[String]("deploymentId").right.get
        case _ =>
          // TODO: Why do we do this?
          throw new Error(response.contentString)
      }
    }
  }
}
