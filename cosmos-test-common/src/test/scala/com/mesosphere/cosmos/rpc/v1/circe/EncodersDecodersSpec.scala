package com.mesosphere.cosmos.rpc.v1.circe

import cats.syntax.either._
import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.AddResponse
import com.mesosphere.universe
import io.circe.jawn.decode
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import scala.util.Right

final class EncodersDecodersSpec extends FreeSpec with PropertyChecks with Matchers {
  import Decoders._
  import Encoders._

  "AddResponse" - {

    "encodes to SupportedPackageDefinition to JSON" in {
      forAll { (v3Package: universe.v4.model.SupportedPackageDefinition) =>
        assertResult(v3Package.asJson)(new AddResponse(v3Package).asJson)
      }
    }

    "decodes from SupportedPackageDefinition JSON" in {
      forAll { (v3Package: universe.v4.model.SupportedPackageDefinition) =>
        assertResult(Right(v3Package)) {
          decode[rpc.v1.model.AddResponse](v3Package.asJson.noSpaces).map(_.packageDefinition)
        }
      }
    }

  }

}
