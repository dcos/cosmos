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

  "Garbage is resolved properly" in {
    forAll(genGarbageCollectorState) { case (stagedPackages, operations, cutoffTime) =>
      val garbage = GarbageCollector.resolveGarbage(stagedPackages, operations, cutoffTime)

      val timeCreated = stagedPackages.toMap
      val pendingUuids = GarbageCollector.getStagedPackageUuids(operations).toSet

      assert(garbage.forall(uuid => timeCreated(uuid).isBefore(cutoffTime)))
      assert(garbage.forall(uuid => !pendingUuids.contains(uuid)))
    }
  }

}

object GarbageCollectorSpec {

  val genInstant: Gen[Instant] = {
    Gen.choose(0L, Long.MaxValue).map(Instant.ofEpochMilli)
  }

  def genInstall(v3Package: Option[V3Package] = None): Gen[Install] = {
    for {
      generatedV3Package <- Generators.genV3Package
      uuid <- Gen.uuid
    } yield Install(uuid, v3Package.getOrElse(generatedV3Package))
  }

  def genUniverseInstall(v3Package: Option[V3Package] = None): Gen[UniverseInstall] = {
    Generators.genV3Package.map { generatedV3Package =>
      UniverseInstall(v3Package.getOrElse(generatedV3Package))
    }
  }

  def genUninstall(v3Package: Option[V3Package] = None): Gen[Uninstall] = {
    Generators.genV3Package.map { generatedV3Package =>
      Uninstall(v3Package.getOrElse(generatedV3Package))
    }
  }

  def genOperation(v3Package: Option[V3Package] = None): Gen[Operation] = {
    Gen.oneOf(genInstall(v3Package), genUniverseInstall(v3Package), genUninstall(v3Package))
  }

  val genErrorResponse: Gen[ErrorResponse] = {
    Gen.const(ErrorResponse("GeneratedError", "This is a generated Error"))
  }

  def genOperationFailure(v3Package: Option[V3Package] = None): Gen[OperationFailure] = {
    for {
      operation <- genOperation(v3Package)
      error <- genErrorResponse
    } yield OperationFailure(operation, error)
  }

  val genPendingStatus: Gen[PendingStatus] = {
    for {
      v3Package <- Generators.genV3Package
      pendingOperation <- genOperation(Some(v3Package))
      failedOperation <- Gen.option(genOperationFailure(Some(v3Package)))
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
