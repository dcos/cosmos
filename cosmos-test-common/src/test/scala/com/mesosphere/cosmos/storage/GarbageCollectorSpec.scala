package com.mesosphere.cosmos.storage

import com.mesosphere.Generators
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.storage.v1.model.FailedStatus
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.Operation
import com.mesosphere.cosmos.storage.v1.model.OperationFailure
import com.mesosphere.cosmos.storage.v1.model.OperationStatus
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.mesosphere.cosmos.storage.v1.model.Uninstall
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import java.time.Instant
import java.util.UUID
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.util.Random

final class GarbageCollectorSpec extends FreeSpec with PropertyChecks {

  import GarbageCollectorSpec._

  "Garbage is resolved such that" - {

    "a garbage element is older than cutoffTime" in {
      forAll(genGarbageCollectorState) {
        case (stagedPackages, operations, cutoffTime) =>
          val garbage = GarbageCollector.resolveGarbage(stagedPackages, operations, cutoffTime)
          val timeCreated = stagedPackages.toMap
          assert(garbage.forall(uuid => timeCreated(uuid).isBefore(cutoffTime)))
      }
    }

    "a garbage element is not pending" in {
      forAll(genGarbageCollectorState) {
        case (stagedPackages, operations, cutoffTime) =>
          val garbage = GarbageCollector.resolveGarbage(stagedPackages, operations, cutoffTime)
          val pendingUuids = GarbageCollector.getStagedPackageUuids(operations).toSet
          assert(garbage.forall(uuid => !pendingUuids.contains(uuid)))
      }
    }

    "a garbage element must be staged" in {
      forAll(genGarbageCollectorState) {
        case (stagedPackages, operations, cutoffTime) =>
          val garbage = GarbageCollector.resolveGarbage(stagedPackages, operations, cutoffTime)
          val stagedUuids = stagedPackages.map(_._1)
          assert(garbage.forall(stagedUuids.contains))
      }
    }

    "a staged element is either too new, pending, or garbage" in {
      forAll(genGarbageCollectorState) {
        case (stagedPackages, operations, cutoffTime) =>
          val garbage = GarbageCollector.resolveGarbage(stagedPackages, operations, cutoffTime)
          val timeCreated = stagedPackages.toMap
          val pendingUuids = GarbageCollector.getStagedPackageUuids(operations).toSet
          val stagedUuids = timeCreated.keySet
          assert(stagedUuids.forall{ staged =>
            timeCreated(staged).isAfter(cutoffTime) ||
              pendingUuids.contains(staged) ||
              garbage.contains(staged)
          })
      }
    }

  }

}

object GarbageCollectorSpec {

  val genInstant: Gen[Instant] = {
    Gen.choose(0L, Long.MaxValue).map(Instant.ofEpochMilli)
  }

  def genInstall(
    genSupportedPackageDefinition
    : Gen[universe.v4.model.SupportedPackageDefinition] = Generators.genSupportedPackageDefinition
  ): Gen[Install] = {
    for {
      supportedPackage <- genSupportedPackageDefinition
      uuid <- Gen.uuid
    } yield Install(uuid, supportedPackage)
  }

  def genUniverseInstall(
    genSupportedPackageDefinition
    : Gen[universe.v4.model.SupportedPackageDefinition] = Generators.genSupportedPackageDefinition
  ): Gen[UniverseInstall] = {
    genSupportedPackageDefinition.map { supportedPackage =>
      UniverseInstall(supportedPackage)
    }
  }

  def genUninstall(
    genSupportedPackageDefinition
    : Gen[universe.v4.model.SupportedPackageDefinition] = Generators.genSupportedPackageDefinition
  ): Gen[Uninstall] = {
    genSupportedPackageDefinition.map { supportedPackage =>
      Uninstall(supportedPackage)
    }
  }

  def genOperation(
    genSupportedPackageDefinition
    : Gen[universe.v4.model.SupportedPackageDefinition] = Generators.genSupportedPackageDefinition
  ): Gen[Operation] = {
    Gen.oneOf(
      genInstall(genSupportedPackageDefinition),
      genUniverseInstall(genSupportedPackageDefinition),
      genUninstall(genSupportedPackageDefinition))
  }

  val genErrorResponse: Gen[ErrorResponse] = {
    Gen.const(ErrorResponse("GeneratedError", "This is a generated Error"))
  }

  def genOperationFailure(
    genSupportedPackageDefinition
    : Gen[universe.v4.model.SupportedPackageDefinition] = Generators.genSupportedPackageDefinition
  ): Gen[OperationFailure] = {
    for {
      operation <- genOperation(genSupportedPackageDefinition)
      error <- genErrorResponse
    } yield OperationFailure(operation, error)
  }

  val genPendingStatus: Gen[PendingStatus] = {
    for {
      supportedPackage <- Generators.genSupportedPackageDefinition
      pendingOperation <- genOperation(supportedPackage)
      failedOperation <- Gen.option(genOperationFailure(supportedPackage))
    } yield PendingStatus(pendingOperation, failedOperation)
  }

  val genFailedStatus: Gen[FailedStatus] = {
    genOperationFailure().map(FailedStatus)
  }

  val genOperationStatus: Gen[OperationStatus] = {
    Gen.oneOf(genPendingStatus, genFailedStatus)
  }

  val genGarbageCollectorState: Gen[(List[(UUID, Instant)], List[OperationStatus], Instant)] = {
    for {
      pendingOnly <- Gen.listOf(genOperationStatus)
      pendingAndStaged <- Gen.listOf(genOperationStatus)
      stagedOnly <- Gen.listOf(genOperationStatus)
      staged = Random.shuffle(stagedOnly ++ pendingAndStaged)
      uuids = GarbageCollector.getStagedPackageUuids(staged)
      creationTimes <- Gen.listOfN(uuids.size, genInstant)
      cutoffTime <- genInstant
    } yield {
      val pending = Random.shuffle(pendingOnly ++ pendingAndStaged)
      val uuidsZipCreationTimes = uuids zip creationTimes
      (uuidsZipCreationTimes, pending, cutoffTime)
    }
  }

}
