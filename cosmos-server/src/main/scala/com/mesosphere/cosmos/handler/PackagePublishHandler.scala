package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.{PublishRequest, PublishResponse}
import com.mesosphere.cosmos.storage.PackageStorage
import com.twitter.util.Future

private[cosmos] final class PackagePublishHandler(
  storage: PackageStorage
) extends EndpointHandler[PublishRequest, PublishResponse] {
  override def apply(publishRequest: PublishRequest)
                    (implicit session: RequestSession): Future[PublishResponse] = {
    storage.putPackageBundle(publishRequest.packageBundle)
    Future.value {
      PublishResponse()
    }
  }
}
