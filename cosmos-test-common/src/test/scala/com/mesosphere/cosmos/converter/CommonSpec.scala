package com.mesosphere.cosmos.converter


import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.util.Success
import scala.util.Try

final class CommonSpec extends FreeSpec with PropertyChecks {

  "PackageCoordinate <=> String" in {
    forAll { (name: String, version: String) =>
      val expected = rpc.v1.model.PackageCoordinate(
        name,
        universe.v3.model.Version(version)
      )
      val intermediate: String = expected.as[String]
      val Success(actual) = intermediate.as[Try[rpc.v1.model.PackageCoordinate]]

      assert(!intermediate.contains("/"))
      assertResult(expected)(actual)
    }
  }
}
