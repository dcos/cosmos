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
import com.mesosphere.universe.v3.model.V3Package
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

  def genInstall(genV3Package: Gen[V3Package] = Generators.genV3Package): Gen[Install] = {
    for {
      v3Package <- genV3Package
      uuid <- Gen.uuid
    } yield Install(uuid, v3Package)
  }

  def genUniverseInstall(genV3Package: Gen[V3Package] = Generators.genV3Package): Gen[UniverseInstall] = {
    genV3Package.map { v3Package =>
      UniverseInstall(v3Package)
    }
  }

  def genUninstall(genV3Package: Gen[V3Package] = Generators.genV3Package): Gen[Uninstall] = {
    genV3Package.map { v3Package =>
      Uninstall(v3Package)
    }
  }

  def genOperation(genV3Package: Gen[V3Package] = Generators.genV3Package): Gen[Operation] = {
    Gen.oneOf(genInstall(genV3Package), genUniverseInstall(genV3Package), genUninstall(genV3Package))
  }

  val genErrorResponse: Gen[ErrorResponse] = {
    Gen.const(ErrorResponse("GeneratedError", "This is a generated Error"))
  }

  def genOperationFailure(genV3Package: Gen[V3Package] = Generators.genV3Package): Gen[OperationFailure] = {
    for {
      operation <- genOperation(genV3Package)
      error <- genErrorResponse
    } yield OperationFailure(operation, error)
  }

  val genPendingStatus: Gen[PendingStatus] = {
    for {
      v3Package <- Generators.genV3Package
      pendingOperation <- genOperation(v3Package)
      failedOperation <- Gen.option(genOperationFailure(v3Package))
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
