package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc
import com.twitter.util.Future

/** This will be used by any reader to observe the state of the queue.
  */
trait ReaderView {
  /** Shows the status of every package in the install queue.
    *
    * @return A map from package coordinate to the state of any pending or
    *         failed operations associated with that package coordinate
    */
  def viewStatus(): Future[Map[rpc.v1.model.PackageCoordinate, OperationStatus]]
}
