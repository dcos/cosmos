package com.mesosphere.cosmos

import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

abstract class IntegrationSpec
  extends fixture.FlatSpec
  with ServiceIntegrationSuite
  with CosmosSpec {

  def createService: Service[Request, Response] = {
    val marathonPackageRunner = new MarathonPackageRunner(adminRouter)
    val sourcesStorage = PackageSourcesStorage.constUniverse(universeUri)

    Cosmos(adminRouter, PackageCache.empty, marathonPackageRunner, sourcesStorage).service
  }

  protected[this] final override val servicePort: Int = port

}
