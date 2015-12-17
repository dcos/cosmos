package com.mesosphere.cosmos

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

abstract class IntegrationSpec
  extends fixture.FlatSpec
  with ServiceIntegrationSuite
  with CosmosSpec {

  def createService: Service[Request, Response] = {
    val adminRouter = Http.newService(s"${Config.DcosHost}:80")
    new Cosmos(PackageCache.empty, new MarathonPackageRunner(adminRouter)).service
  }

  protected[this] final override val servicePort: Int = port

}
