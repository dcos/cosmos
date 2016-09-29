package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.ErrorResponse

import com.twitter.util.Future

trait PackageQueueReader {

  def read(
    pkg: PackageCoordinate
  ): Future[(Option[PackageQueueContents], Option[ErrorResponse])]

  def readAll(): Future[Map[PackageCoordinate,
                           (Option[PackageQueueContents], Option[ErrorResponse])]]

}
