package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc
import com.twitter.util.Future

/** This will be used by the producer to observe the state of
  * the install queue
  */
trait ProducerView {
  /** Adds an operation on a package to the install queue.
    *
    * Fails the resulting future with an exception when there is already an operation outstanding
    * on the package coordinate.
    *
    * @param packageCoordinate the package coordinate on which the operation
    *                          should be performed.
    * @param operation The operation to be performed
    * @return a successful future if the operation was added to the queue, else a failed future
    *         of [[com.mesosphere.cosmos.OperationInProgress]] if the operation was not added.
    */
  def add(packageCoordinate: rpc.v1.model.PackageCoordinate, operation: Operation): Future[Unit]
}
