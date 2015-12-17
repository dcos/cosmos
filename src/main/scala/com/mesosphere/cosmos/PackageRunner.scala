package com.mesosphere.cosmos

import com.twitter.util.Future
import io.finch.Output

/** The service that packages are installed to; currently defaults to Marathon in DCOS. */
trait PackageRunner {

  /** Execute the package described by the given JSON configuration.
    *
    * @param renderedConfig the fully-specified configuration of the package to run
    * @return An HTTP status code and message indicating success or failure.
    */
  def launch(renderedConfig: String): Future[Output[String]]

}
