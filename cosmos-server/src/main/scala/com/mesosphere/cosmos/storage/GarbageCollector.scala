package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.storage.installqueue.ReaderView
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.PendingStatus
import com.twitter.util.Future
import java.time.Instant
import java.util.UUID

final class GarbageCollector private(stagedPackageStorage: StagedPackageStorage, installQueueReader: ReaderView) {

  private[this] val timeout = 60*60

  def collectGarbage(): Future[Unit] = {
    listGarbage().flatMap { garbage =>
      Future.join(garbage.map(stagedPackageStorage.delete).toSeq)
    }
  }

  private[this] def listGarbage(): Future[Set[UUID]] = {
    for {
      staged <- listStagedPackages()
      pending <- listPendingStagedPackages()
      garbage <- selectGarbage(staged -- pending)
    } yield garbage
  }

  private[this] def selectGarbage(possibleGarbage: Set[UUID]): Future[Set[UUID]] = {
    val garbage = possibleGarbage.map { id =>
      isGarbage(id).map {
        case true => Some(id)
        case false => None
      }
    }
    Future.collect(garbage.toSeq).map(_.flatten.toSet)
  }

  private[this] def isGarbage(id: UUID): Future[Boolean] = {
    stagedPackageStorage.getCreationTime(id).map { creationTime =>
      val cutoffTime = Instant.now().minusSeconds(timeout)
      creationTime.isBefore(cutoffTime)
    }
  }

  private[this] def listStagedPackages(): Future[Set[UUID]] = {
    stagedPackageStorage.list().map(_.toSet)
  }

  private[this] def listPendingStagedPackages(): Future[Set[UUID]] = {
    installQueueReader.viewStatus().map { statuses =>
      statuses.values.flatMap {
        case PendingStatus(Install(stagedPackageID, _), _) => Some(stagedPackageID)
        case _ => None
      }.toSet
    }
  }

}
