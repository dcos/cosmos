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
  import EncodersDecodersSpec.genLocalPackage
  import Encoders._
  import Decoders._

  "For all LocalPackage; LocalPackage => JSON => LocalPackage" in {
    forAll (genLocalPackage) { localPackage =>
      val string = localPackage.asJson.noSpaces
      decode[rpc.v1.model.LocalPackage](string) shouldBe Xor.Right(localPackage)
    }
  }

  "AddResponse" - {

    "encodes to V3Package JSON" in {
      forAll (genV3Package) { v3Package =>
        assertResult(v3Package.asJson)(new AddResponse(v3Package).asJson)
      }
    }

    "decodes from V3Package JSON" in {
      forAll (genV3Package) { v3Package =>
        assertResult(Xor.Right(v3Package)) {
          decode[rpc.v1.model.AddResponse](v3Package.asJson.noSpaces).map(_.v3Package)
        }
      }
    }

  }

}

object EncodersDecodersSpec {
  implicit val genPackageCoordinate: Gen[rpc.v1.model.PackageCoordinate] = for {
    name <- Gen.alphaStr
    version <- genVersion
  } yield rpc.v1.model.PackageCoordinate(name, version)

  implicit val genErrorResponse: Gen[rpc.v1.model.ErrorResponse] = for {
    tp <- Gen.alphaStr
    message <- Gen.alphaStr
  } yield rpc.v1.model.ErrorResponse(tp, message, None)

  implicit val genInstall: Gen[rpc.v1.model.Install] = for {
    stagedPackageId <- Gen.uuid
    v3Package <- genV3Package
  } yield rpc.v1.model.Install(stagedPackageId, v3Package)

  implicit val genUniverseInstall: Gen[rpc.v1.model.UniverseInstall] =
    genV3Package.map(rpc.v1.model.UniverseInstall)

  implicit val genUninstall: Gen[rpc.v1.model.Uninstall] =
    Gen.option(genV3Package).map(rpc.v1.model.Uninstall)

  implicit val genOperation: Gen[rpc.v1.model.Operation] = Gen.oneOf(
    genInstall,
    genUniverseInstall,
    genUninstall
  )

  implicit val genNotInstalled: Gen[rpc.v1.model.NotInstalled] =
    genV3Package.map(rpc.v1.model.NotInstalled)

  implicit val genInstalling: Gen[rpc.v1.model.Installing] =
    genV3Package.map(rpc.v1.model.Installing)

  implicit val genInstalled: Gen[rpc.v1.model.Installed] =
    genV3Package.map(rpc.v1.model.Installed)

  implicit val genUninstalling: Gen[rpc.v1.model.Uninstalling] = Gen.oneOf(
    genPackageCoordinate.map(pc => rpc.v1.model.Uninstalling(Left(pc))),
    genV3Package.map(metadata => rpc.v1.model.Uninstalling(Right(metadata)))
  )

  implicit val genFailed: Gen[rpc.v1.model.Failed] = for {
    operation <- genOperation
    error <- genErrorResponse
    metadata <- genV3Package
  } yield rpc.v1.model.Failed(operation, error, metadata)

  implicit val genInvalid: Gen[rpc.v1.model.Invalid] = for {
    error <- genErrorResponse
    pc <- genPackageCoordinate
  } yield rpc.v1.model.Invalid(error, pc)

  implicit val genLocalPackage: Gen[rpc.v1.model.LocalPackage] = Gen.oneOf(
    genNotInstalled,
    genInstalling,
    genInstalled,
    genUninstalling,
    genFailed,
    genInvalid
  )
}
