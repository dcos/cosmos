package com.mesosphere.cosmos

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

abstract class IntegrationSpec
  extends fixture.FlatSpec
  with ServiceIntegrationSuite
  with CosmosSpec {

  def createService: Service[Request, Response] = {
    val adminRouterUri = dcosHost()
    val dcosClient = Services.adminRouterClient(adminRouterUri)
    val adminRouter = new AdminRouter(adminRouterUri, dcosClient)
    new Cosmos(PackageCache.empty, new MarathonPackageRunner(adminRouter), new UninstallHandler(adminRouter)).service
  }

  protected[this] final override val servicePort: Int = port

}
