package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.rpc
import com.twitter.util.Future

/** This will be used by the producer to observe the state of
  * the install queue
  */
trait ProducerView {
  /** Adds an operation on a package to the install queue. It will return
    * an object signaling failure when there is already an operation outstanding
    * on the package coordinate.
    *
    * @param packageCoordinate the package coordinate on which the operation
    *                          should be performed.
    * @param operation The operation to be performed
    * @return Created if the operation was added to the queue, else AlreadyExists
    *         if the operation was not added to the queue.
    */
  def add(
    packageCoordinate: rpc.v1.model.PackageCoordinate,
    operation: Operation
  ): Future[AddResult]
}
