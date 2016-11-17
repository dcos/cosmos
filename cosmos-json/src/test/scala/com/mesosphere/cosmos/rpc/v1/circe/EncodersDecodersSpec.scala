package com.mesosphere.cosmos.rpc.v1.circe

import cats.data.Xor
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.AddResponse
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model.PackageDefinitionSpec._
import io.circe.jawn.decode
import io.circe.syntax._
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class EncodersDecodersSpec extends FreeSpec with PropertyChecks with Matchers {
  import EncodersDecodersSpec.localPackageGen
  import Encoders._
  import Decoders._

  "For all LocalPackage; LocalPackage => JSON => LocalPackage" in {
    forAll (localPackageGen) { localPackage =>
      val string = localPackage.asJson.noSpaces
      decode[rpc.v1.model.LocalPackage](string) shouldBe Xor.Right(localPackage)
    }
  }

  "AddResponse" - {

    "encodes to V3Package JSON" in {
      forAll (v3PackageGen) { v3Package =>
        assertResult(v3Package.asJson)(new AddResponse(v3Package).asJson)
      }
    }

    "decodes from V3Package JSON" in {
      forAll (v3PackageGen) { v3Package =>
        assertResult(Xor.Right(v3Package)) {
          decode[rpc.v1.model.AddResponse](v3Package.asJson.noSpaces).map(_.v3Package)
        }
      }
    }

  }

}

object EncodersDecodersSpec {
  implicit val packageCoordinateGen = for {
    name <- Gen.alphaStr
    version <- versionGen
  } yield rpc.v1.model.PackageCoordinate(name, version)

  implicit val errorResponseGen = for {
    tp <- Gen.alphaStr
    message <- Gen.alphaStr
  } yield rpc.v1.model.ErrorResponse(tp, message, None)

  implicit val notInstalledGen = packageDefinitionGen.map(rpc.v1.model.NotInstalled(_))

  implicit val installingGen = packageDefinitionGen.map(rpc.v1.model.Installing(_))

  implicit val installedGen = packageDefinitionGen.map(rpc.v1.model.Installing(_))

  implicit val uninstallingGen = Gen.oneOf(
    packageCoordinateGen.map(pc => rpc.v1.model.Uninstalling(Left(pc))),
    packageDefinitionGen.map(metadata => rpc.v1.model.Uninstalling(Right(metadata)))
  )

  implicit val failedGen = for {
    operation <- Gen.alphaStr // TODO: Update after PackageOps PR
    error <- errorResponseGen
    metadata <- packageDefinitionGen
  } yield rpc.v1.model.Failed(operation, error, metadata)

  implicit val invalidGen = for {
    error <- errorResponseGen
    pc <- packageCoordinateGen
  } yield rpc.v1.model.Invalid(error, pc)

  implicit val localPackageGen: Gen[rpc.v1.model.LocalPackage] = Gen.oneOf(
    notInstalledGen,
    installingGen,
    installedGen,
    uninstallingGen,
    failedGen,
    invalidGen
  )
}
