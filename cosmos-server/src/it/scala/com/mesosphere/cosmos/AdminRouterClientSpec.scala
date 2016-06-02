package com.mesosphere.cosmos

import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.mesosphere.universe.v3.DcosReleaseVersionParser
import com.twitter.util.Await
import org.scalatest.FreeSpec

class AdminRouterClientSpec extends FreeSpec {

  "AdminRouterSpec" - {

    "can fetch /dcos-metadata/dcos-version.json and decode" in {
      val dcosVersion = Await.result(adminRouter.getDcosVersion())

      assert(dcosVersion.version >= DcosReleaseVersionParser.parseUnsafe("1.6.1"))
    }

  }

}
