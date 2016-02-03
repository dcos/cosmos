package com.mesosphere.cosmos

import com.mesosphere.cosmos.handler._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

abstract class IntegrationSpec
  extends fixture.FlatSpec
  with ServiceIntegrationSuite
  with CosmosSpec {

  def createService: Service[Request, Response] = {
    val adminRouterUri = adminRouterHost
    val dcosClient = Services.adminRouterClient(adminRouterUri).get
    val adminRouter = new AdminRouter(adminRouterUri, dcosClient)

    // these two imports provide the implicit DecodeRequest instances needed to instantiate Cosmos
    import com.mesosphere.cosmos.circe.Decoders._
    import com.mesosphere.cosmos.circe.Encoders._
    import io.finch.circe._
    val marathonPackageRunner = new MarathonPackageRunner(adminRouter)
    //TODO: Get rid of this duplication
    new Cosmos(
      PackageCache.empty,
      marathonPackageRunner,
      new UninstallHandler(adminRouter, PackageCache.empty),
      new PackageInstallHandler(PackageCache.empty, marathonPackageRunner),
      new PackageRenderHandler(PackageCache.empty),
      new PackageSearchHandler(PackageCache.empty),
      new PackageImportHandler,
      new PackageDescribeHandler(PackageCache.empty),
      new ListVersionsHandler(PackageCache.empty),
      new ListHandler(adminRouter, PackageCache.empty)
    ).service
  }

  protected[this] final override val servicePort: Int = port

}
