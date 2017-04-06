package com.mesosphere.universe.v3.model

import com.mesosphere.universe
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackagingVersionSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  "PackagingVersion.show" - {
    forAll(universe.v3.model.PackagingVersionTestOps.validPackagingVersions) { (version, string) =>
        s"PackagingVersion $string" in {
          version.show should be(string)
        }
    }
  }

  "PackagingVersions.allVersions" in {
    val allVersions = universe.v3.model.PackagingVersionTestOps.validPackagingVersions.map(_._1)
    allVersions should be(PackagingVersion.allVersions)
  }

}
