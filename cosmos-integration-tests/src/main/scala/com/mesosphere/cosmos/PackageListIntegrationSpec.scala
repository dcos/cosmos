package com.mesosphere.cosmos

import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.util.RoundTrip
import com.twitter.bijection.Conversion.asMethod
import java.util.UUID
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

final class PackageListIntegrationSpec extends FeatureSpec with Matchers {

  feature("The package/list endpoint") {
    scenario("should list installed packages") {
      RoundTrips.withInstallV1("helloworld").runWith { ir =>
        val pkg = Requests.describePackage(ir.packageName, Some(ir.packageVersion)).`package`
        val pkgs = Requests.listPackages()
        pkgs.map(_.appId) should contain(ir.appId)
        pkgs.find(_.appId == ir.appId).get.packageInformation.shouldBe(
          pkg.as[rpc.v1.model.InstalledPackageInformation]
        )
      }
    }
    scenario("Issue #251: should include packages whose repositories have been removed") {
      // TODO: Change this to remove helloworld's repo when describe returns repo information
      RoundTrips.withInstallV1("helloworld").runWith { ir =>
        val pkg = Requests.describePackage(ir.packageName, Some(ir.packageVersion)).`package`
        RoundTrips.withDeletedRepository(Some("V4TestUniverse")).runWith { _ =>
          val pkgs = Requests.listPackages()
          pkgs.map(_.appId) should contain(ir.appId)
          pkgs.find(_.appId == ir.appId).get.packageInformation.shouldBe(
            pkg.as[rpc.v1.model.InstalledPackageInformation]
          )
        }
      }
    }
    scenario("Issue #124: should respond with packages sorted by name and app id") {
      val names = List("linkerd", "linkerd", "zeppelin", "jenkins", "cassandra")
      val withInstalls = RoundTrip.sequence(names.map { name =>
        RoundTrips.withInstallV1(name, appId = Some(UUID.randomUUID().as[AppId]))
      })
      withInstalls.runWith { installs =>
        val expectedPkgs = installs.map(i => (i.packageName, i.appId))
        val pkgs = Requests.listPackages().map { i =>
          (i.packageInformation.packageDefinition.name, i.appId)
        }
        pkgs shouldBe expectedPkgs.sorted
      }
    }
  }

}
