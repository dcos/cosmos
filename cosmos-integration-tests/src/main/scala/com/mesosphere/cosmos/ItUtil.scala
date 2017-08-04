package com.mesosphere.cosmos

import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.twitter.util.Await
import scala.concurrent.duration._

object ItUtil {

  def getTestUniverseRepoByName: String = {
    // TODO: Implement this better.
    ???
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
