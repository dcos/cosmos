package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.Session
import com.mesosphere.cosmos.test.InstallQueueFixture
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.TestUtil
import com.mesosphere.universe.test.TestingPackages
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import com.twitter.util.Await
import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec

final class ServiceStartSpec extends FreeSpec with InstallQueueFixture with BeforeAndAfter {

  import ServiceStartSpec._

  after {
    val uninstallCassandra =
      rpc.v1.model.UninstallRequest("cassandra", appId = None, all = Some(true))
    val uninstallKafka =
      rpc.v1.model.UninstallRequest("kafka", appId = None, all = Some(true))
    val uninstallHelloworld =
      rpc.v1.model.UninstallRequest("helloworld", appId = None, all = Some(true))

    CosmosClient.submit(CosmosRequests.packageUninstall(uninstallCassandra))
    CosmosClient.submit(CosmosRequests.packageUninstall(uninstallKafka))
    val _ = CosmosClient.submit(CosmosRequests.packageUninstall(uninstallHelloworld))

    // TODO package-add: Remove uploaded packages from storage
  }

  "The /service/start endpoint" - {

    "can successfully 'run' an uploaded package without a Marathon template" in {
      val packageDef = TestingPackages.MinimalV3ModelV3PackageDefinition
      val uploadResponse = addUploadedPackage(packageDef)
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

    "can successfully run an uploaded package with a Marathon template" in {
      val packageDef = TestingPackages.HelloWorldV3Package
      val uploadResponse = addUploadedPackage(packageDef)
      assertResult(Status.Accepted)(uploadResponse.status)

      awaitEmptyInstallQueue()

      val startResponse = startService(packageDef.name)
      assertResult(Status.Ok)(startResponse.status)
      assertResult(Some(rpc.MediaTypes.ServiceStartResponse.show))(startResponse.contentType)

      val typedResponse = decode[rpc.v1.model.ServiceStartResponse](startResponse.contentString)
      assertResult(packageDef.name)(typedResponse.packageName)
      assertResult(packageDef.version)(typedResponse.packageVersion)

      val Some(appId) = typedResponse.appId
      assertResult("/helloworld")(appId.toString)

      val marathonApp = getMarathonApp(appId)
      assertResult(appId)(marathonApp.id)
    }

    "can successfully run an installed package from Universe with a Marathon template" in {
      val packageName = "cassandra"
      val addResponse = addUniversePackage(packageName)
      assertResult(Status.Accepted)(addResponse.status)

      awaitEmptyInstallQueue()

      val startResponse = startService(packageName)
      assertResult(Status.Ok)(startResponse.status)
      assertResult(Some(rpc.MediaTypes.ServiceStartResponse.show))(startResponse.contentType)

      val typedResponse = decode[rpc.v1.model.ServiceStartResponse](startResponse.contentString)
      assertResult(packageName)(typedResponse.packageName)

      val Some(appId) = typedResponse.appId
      assertResult("/cassandra")(appId.toString)

      val marathonApp = getMarathonApp(appId)
      assertResult(appId)(marathonApp.id)
    }

    "return a ServiceAlreadyStarted when trying to start a service twice" in {
      val packageName = "kafka"
      val addResponse = addUniversePackage(packageName)
      assertResult(Status.Accepted)(addResponse.status)

      awaitEmptyInstallQueue()

      val startResponse = startService(packageName)
      assertResult(Status.Ok)(startResponse.status)
      assertResult(Some(rpc.MediaTypes.ServiceStartResponse.show))(startResponse.contentType)

      val typedResponse = decode[rpc.v1.model.ServiceStartResponse](startResponse.contentString)
      assertResult(packageName)(typedResponse.packageName)

      val Some(appId) = typedResponse.appId
      assertResult("/kafka")(appId.toString)

      val marathonApp = getMarathonApp(appId)
      assertResult(appId)(marathonApp.id)

      val startAgainResponse = startService(packageName)
      assertResult(Status.Conflict)(startAgainResponse.status)
      assertResult(Some(rpc.MediaTypes.ErrorResponse.show))(startAgainResponse.contentType)

      val typedErrorResponse = decode[ErrorResponse](startAgainResponse.contentString)
      assertResult(classOf[ServiceAlreadyStarted].getSimpleName)(typedErrorResponse.`type`)
    }

    "can successfully start a service from a v4 package" in {
      val packageName = "helloworld"
      val packageVersion = universe.v3.model.Version("0.4.1")
      val addResponse = addUniversePackage(packageName, Some(packageVersion))
      assertResult(Status.Accepted)(addResponse.status)

      awaitEmptyInstallQueue()

      val startResponse = startService(packageName)
      assertResult(Status.Ok)(startResponse.status)
      assertResult(Some(rpc.MediaTypes.ServiceStartResponse.show))(startResponse.contentType)

      val typedResponse = decode[rpc.v1.model.ServiceStartResponse](startResponse.contentString)
      assertResult(packageName)(typedResponse.packageName)

      val Some(appId) = typedResponse.appId
      assertResult("/helloworld")(appId.toString)

      val marathonApp = getMarathonApp(appId)
      assertResult(appId)(marathonApp.id)
    }

  }

}

object ServiceStartSpec {

  def addUploadedPackage(v3Package: universe.v3.model.V3Package): Response = {
    val packageData = Buf.ByteArray.Owned(TestUtil.buildPackage(v3Package))
    val request = CosmosRequests.packageAdd(packageData)
    CosmosClient.submit(request)
  }

  def addUniversePackage(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version] = None
  ): Response = {
    val addRequest = rpc.v1.model.UniverseAddRequest(packageName, packageVersion = packageVersion)
    val request = CosmosRequests.packageAdd(addRequest)
    CosmosClient.submit(request)
  }

  def getMarathonApp(appId: AppId): MarathonApp = {
    val response = Await.result(CosmosIntegrationTestClient.adminRouter.getApp(appId))
    response.app
  }

  def startService(packageName: String): Response = {
    val request = CosmosRequests.serviceStart(rpc.v1.model.ServiceStartRequest(packageName))
    CosmosClient.submit(request)
  }

}
