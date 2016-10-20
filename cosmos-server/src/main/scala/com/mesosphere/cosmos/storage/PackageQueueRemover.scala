package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.twitter.util.Future

trait PackageQueueRemover {

  def done(
    pkg: PackageCoordinate,
    error: Option[ErrorResponse]
  ): Future[Unit]

}
