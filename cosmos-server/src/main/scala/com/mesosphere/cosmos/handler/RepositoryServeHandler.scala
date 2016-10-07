package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.universe.v3.model.Repository
import com.twitter.util.Future

private[cosmos] final class RepositoryServeHandler(
  storage: PackageStorage
) extends EndpointHandler[Unit, Repository] {
  override def apply(req: Unit)
                    (implicit session: RequestSession): Future[Repository] = {
    storage.getRepository
  }
}
