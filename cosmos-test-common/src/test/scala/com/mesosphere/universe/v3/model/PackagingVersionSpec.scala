package com.mesosphere.universe.v3.model

import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackagingVersionSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  "PackagingVersion.show" - {
    forAll(TestingPackages.validPackagingVersions) { (version, string) =>
        s"PackagingVersion $string" in {
          version.show should be(string)
        }
    }
  }

  "PackagingVersions.allVersions" in {
    val allVersions = TestingPackages.validPackagingVersions.map(_._1)
    allVersions should be(universe.v4.model.PackagingVersion.allVersions)
  }

}
