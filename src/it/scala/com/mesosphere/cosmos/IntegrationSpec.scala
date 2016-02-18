package com.mesosphere.cosmos

import java.nio.file.Files

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
    val sourcesStorage = IntegrationTests.constUniverse(universeUri)

    val tempDir = Files.createTempDirectory("cosmos-integration")
    tempDir.toFile.deleteOnExit

    Cosmos(adminRouter, marathonPackageRunner, sourcesStorage, tempDir).service
  }

  protected[this] final override val servicePort: Int = port

}
