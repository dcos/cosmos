package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.ErrorResponse

import com.twitter.util.Future

trait PackageQueueRemover {

  def done(
    pkg: PackageCoordinate,
    error: Option[ErrorResponse]
  ): Future[Unit]

}
