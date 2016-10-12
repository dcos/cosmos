package com.mesosphere.universe.v3.model

import org.scalatest.FreeSpec

final class PackagingVersionSpec extends FreeSpec {

  "PackagingVersion.show" - {

    "V2PackagingVersion" in {
      assertResult("2.0")(V2PackagingVersion.show)
    }

    "V3PackagingVersion" in {
      assertResult("3.0")(V3PackagingVersion.show)
    }

  }

  "PackagingVersion$.allVersions" in {
    assertResult(Seq(V2PackagingVersion, V3PackagingVersion)) {
      PackagingVersion.allVersions
    }
  }

}
