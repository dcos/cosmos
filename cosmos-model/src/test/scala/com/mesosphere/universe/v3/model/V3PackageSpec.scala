package com.mesosphere.universe.v3.model

import com.mesosphere.universe.v3.model.PackageDefinition._
import org.scalatest.FreeSpec

class V3PackageSpec extends FreeSpec {

  "V3Package" - {
    "Ordering should work" in {
      val expected = List(
        v3Package("pkg1", Version("1.0-1"), ReleaseVersion(1)),
        v3Package("pkg1", Version("1.0-2"), ReleaseVersion(2)),
        v3Package("pkg1", Version("1.0-3"), ReleaseVersion(3)),
        v3Package("pkg2", Version("1.0"), ReleaseVersion(1)),
        v3Package("pkg2", Version("2.0"), ReleaseVersion(2)),
        v3Package("pkg3", Version("1.0"), ReleaseVersion(3)),
        v3Package("pkg4", Version("1.0"), ReleaseVersion(4)),
        v3Package("pkg5", Version("1.0-1"), ReleaseVersion(1)),
        v3Package("pkg5", Version("2.0-1"), ReleaseVersion(2)),
        v3Package("pkg5", Version("1.1-1"), ReleaseVersion(3))
      )

      val list = List(
        v3Package("pkg1", Version("1.0-1"), ReleaseVersion(1)),
        v3Package("pkg1", Version("1.0-2"), ReleaseVersion(2)),
        v3Package("pkg5", Version("2.0-1"), ReleaseVersion(2)),
        v3Package("pkg5", Version("1.0-1"), ReleaseVersion(1)),
        v3Package("pkg1", Version("1.0-3"), ReleaseVersion(3)),
        v3Package("pkg2", Version("1.0"), ReleaseVersion(1)),
        v3Package("pkg2", Version("2.0"), ReleaseVersion(2)),
        v3Package("pkg3", Version("1.0"), ReleaseVersion(3)),
        v3Package("pkg5", Version("1.1-1"), ReleaseVersion(3)),
        v3Package("pkg4", Version("1.0"), ReleaseVersion(4))
      )

      val sorted = list.sorted

      assertResult(expected)(sorted)
    }
  }

  def v3Package(name: String, version: Version, relVer: ReleaseVersion): V3Package = {
    V3Package(
      V3PackagingVersion.instance,
      name,
      version,
      relVer,
      "does@not.matter",
      "doesn't matter"
    )
  }

}
