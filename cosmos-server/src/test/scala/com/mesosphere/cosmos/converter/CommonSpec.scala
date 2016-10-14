package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe.v3.model.PackageDefinition
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.util.Success
import scala.util.Try

final class CommonSpec extends FreeSpec with PropertyChecks {

  "PackageCoordinate <=> String" in {
    forAll { (name: String, version: String) =>
      val pc = PackageCoordinate(name, PackageDefinition.Version(version))
      val expected: PackageCoordinate = pc
      val intermediate: String = pc.as[String]
      val Success(actual) = intermediate.as[Try[PackageCoordinate]]

      assert(!intermediate.contains("/"))
      assertResult(expected)(actual)
    }
  }
}
