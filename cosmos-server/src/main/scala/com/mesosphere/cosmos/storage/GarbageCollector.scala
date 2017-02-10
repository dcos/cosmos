package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.storage.installqueue.ReaderView
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.OperationStatus
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.twitter.util.Future
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

final class GarbageCollector private(
  stagedPackageStorage: StagedPackageStorage,
  installQueueReader: ReaderView,
  gracePeriod: FiniteDuration
) extends (() => Future[Unit]){

  import GarbageCollector._

  override def apply(): Future[Unit] = {
    val stagedPackages = stagedPackageStorage.list().flatMap { ids =>
      val packages = ids.map { id =>
        stagedPackageStorage.getCreationTime(id)
          .map(_.map((id, _)))
      }
      Future.collect(packages).map(_.toList.flatten)
    }
    val operations = installQueueReader.viewStatus().map(_.values.toList)
    val cutoffTime = Instant.now().minusSeconds(gracePeriod.toSeconds)
    for {
      stagedPackages <- stagedPackages
      operations <- operations
      garbage = resolveGarbage(stagedPackages, operations, cutoffTime)
      _ <- Future.join(garbage.map(stagedPackageStorage.delete).toSeq)
    } yield ()
  }

}

object GarbageCollector {

  def apply(
    stagedPackageStorage: StagedPackageStorage,
    installQueueReader: ReaderView,
    gracePeriod: FiniteDuration
  ): GarbageCollector = {
    new GarbageCollector(stagedPackageStorage, installQueueReader, gracePeriod)
  }

  def resolveGarbage(
    stagedPackages: List[(UUID, Instant)],
    operations: List[OperationStatus],
    cutoffTime: Instant
  ): Set[UUID] = {
    val creationTimes = stagedPackages.toMap
    val staged = creationTimes.keySet
    val pending = getStagedPackageUuids(operations).toSet
    val possibleGarbage = staged -- pending
    possibleGarbage
      .filter(id => creationTimes(id).isBefore(cutoffTime))
  }

  def getStagedPackageUuids(operations: List[OperationStatus]): List[UUID] = {
    operations.flatMap {
      case PendingStatus(Install(stagedPackageID, _), _) => Some(stagedPackageID)
      case _ => None
    }
  }

}
