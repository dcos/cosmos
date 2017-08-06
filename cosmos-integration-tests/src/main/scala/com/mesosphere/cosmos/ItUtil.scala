package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.twitter.util.Await
import scala.concurrent.duration._

object ItUtil {

  def getTestUniverseRepoByName(implicit testContext: TestContext): String = {
    Requests.getRepository("V4TestUniverse").get.uri.toString
  }

  def waitForDeployment(adminRouter: AdminRouter)(attempts: Int): Boolean = {
    Stream.tabulate(attempts) { _ =>
      Thread.sleep(1.second.toMillis)
      val deployments = Await.result {
        adminRouter.listDeployments()(CosmosIntegrationTestClient.Session)
      }

      deployments.isEmpty
    }.dropWhile(done => !done).nonEmpty
  }

}
