package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.thirdparty.marathon.MarathonApp
import com.twitter.util.Future
import io.circe.Json

/** The service that packages are installed to; currently defaults to Marathon in DCOS. */
trait PackageRunner {

  /** Execute the package described by the given JSON configuration.
    *
    * @param renderedConfig the fully-specified configuration of the package to run
    * @return The response from Marathon, if the request was successful.
    */
  def launch(renderedConfig: Json): Future[MarathonApp]

}
