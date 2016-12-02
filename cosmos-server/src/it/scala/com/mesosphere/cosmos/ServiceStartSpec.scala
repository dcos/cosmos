package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.InstallQueueFixture
import com.mesosphere.universe
import com.mesosphere.universe.TestUtil
import com.mesosphere.universe.test.TestingPackages
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import org.scalatest.FreeSpec

final class ServiceStartSpec extends FreeSpec with InstallQueueFixture {

  import ServiceStartSpec._

  "The /service/start endpoint" - {

    "can successfully 'run' an installed package without a marathon template" in {
      val packageDef = TestingPackages.MinimalV3ModelV3PackageDefinition
      val uploadResponse = uploadPackage(packageDef)
      assertResult(Status.Accepted)(uploadResponse.status)

      awaitEmptyInstallQueue()

      val startResponse = startService(packageDef.name)
      assertResult(Status.Ok)(startResponse.status)
      assertResult(Some(rpc.MediaTypes.ServiceStartResponse.show))(startResponse.contentType)

      val typedResponse = decode[rpc.v1.model.ServiceStartResponse](startResponse.contentString)
      assertResult(packageDef.name)(typedResponse.packageName)
      assertResult(packageDef.version)(typedResponse.packageVersion)
      assert(typedResponse.appId.isEmpty)
    }

  }

}

object ServiceStartSpec {

  def uploadPackage(v3Package: universe.v3.model.V3Package): Response = {
    val packageData = Buf.ByteArray.Owned(TestUtil.buildPackage(v3Package))
    val request = CosmosRequests.packageAdd(packageData)
    CosmosClient.submit(request)
  }

  def startService(packageName: String): Response = {
    val request = CosmosRequests.serviceStart(rpc.v1.model.ServiceStartRequest(packageName))
    CosmosClient.submit(request)
  }

}
