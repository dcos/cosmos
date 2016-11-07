package com.mesosphere.cosmos

import _root_.io.circe.Json
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.twitter.util.Future

/** The service that packages are installed to; currently defaults to Marathon in DCOS. */
trait PackageRunner {

  /** Execute the package described by the given JSON configuration.
    *
    * @param renderedConfig the fully-specified configuration of the package to run
    * @return The response from Marathon, if the request was successful.
    */
  def launch(renderedConfig: Json)(implicit session: RequestSession): Future[MarathonApp]

}
