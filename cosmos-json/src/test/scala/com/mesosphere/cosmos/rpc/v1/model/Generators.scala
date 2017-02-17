package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.Generators._
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage
import org.scalacheck.Gen

object Generators {

  val genPackageCoordinate: Gen[rpc.v1.model.PackageCoordinate] = for {
    name <- Gen.alphaStr
    version <- genVersion
  } yield rpc.v1.model.PackageCoordinate(name, version)

  val genErrorResponse: Gen[rpc.v1.model.ErrorResponse] = for {
    tp <- Gen.alphaStr
    message <- Gen.alphaStr
  } yield rpc.v1.model.ErrorResponse(tp, message, None)

  val genInstall: Gen[storage.v1.model.Install] = for {
    stagedPackageId <- Gen.uuid
    v3Package <- genV3Package
  } yield storage.v1.model.Install(stagedPackageId, v3Package)

  val genUniverseInstall: Gen[storage.v1.model.UniverseInstall] =
    genV3Package.map(storage.v1.model.UniverseInstall)

  val genUninstall: Gen[storage.v1.model.Uninstall] =
    genV3Package.map(storage.v1.model.Uninstall)

  val genOperation: Gen[storage.v1.model.Operation] = Gen.oneOf(
    genInstall,
    genUniverseInstall,
    genUninstall
  )

}
