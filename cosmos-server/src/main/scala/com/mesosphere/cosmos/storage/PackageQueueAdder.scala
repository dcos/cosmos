package com.mesosphere.cosmos.storage

import com.twitter.util.Future

trait PackageQueueAdder {

  def add(
    pkg: PackageCoordinate,
    content: PackageQueueContents
  ): Future[PackageAddResult]

}
