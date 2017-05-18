package com.mesosphere.cosmos.janitor

import java.nio.file.Paths

import com.mesosphere.cosmos.janitor.SdkJanitor._
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.cosmos.storage.v1.model.Failed
import com.mesosphere.cosmos.storage.v1.model.InProgress
import com.mesosphere.cosmos.storage.v1.model.UninstallStatus
import com.mesosphere.cosmos.storage.v1.model.UninstallStatus._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.slf4j.Logger

trait Tracker {
  def startUninstall(appId: AppId): UninstallClaim
  def failUninstall(appId: AppId): Unit
  def completeUninstall(appId: AppId): Unit
}

/** Tracks work being done by the Janitor */
final class JanitorTracker(
  curator: CuratorFramework,
  lock: UninstallLock
) extends Tracker {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)


  override def startUninstall(appId: AppId): UninstallClaim = {
    // Does this JVM already own the lock?
    if (lock.isLockedByThisProcess(appId)) {
      UninstallAlreadyClaimed
    } else {
      // Does some other JVM already own the lock?
      lock.lock(appId) match {
        case true =>
          logger.info("Acquired uninstall lock for app: {}", appId)
          markInProgress(appId)
          UninstallClaimed
        case false =>
          logger.info("Failed to acquire uninstall lock for app: {}", appId)
          UninstallAlreadyClaimed
      }
    }
  }

  override def failUninstall(appId: AppId): Unit = {
    markFailed(appId)
    lock.unlock(appId)
  }

  override def completeUninstall(appId: AppId): Unit = {
    lock.unlock(appId)
    deleteZkRecord(appId)
  }

  private[janitor] def getStatus(appId: AppId): UninstallStatus = {
    StorageEnvelope.decodeData[UninstallStatus](curator.getData.forPath(statusPath(appId)))
  }

  private def statusPath(appId: AppId): String = {
    // uninstalls/<name>/host
    Paths.get(UninstallFolder, appId.toString, "status").toString
  }

  private def rootPath(appId: AppId): String = {
    // uninstalls/<name>
    Paths.get(UninstallFolder, appId.toString).toString
  }

  private def setStatus(appId: AppId, status: UninstallStatus): Unit = {
    val _ = curator.setData()
      .forPath(statusPath(appId), StorageEnvelope.encodeData(status))
  }

  private def createAndSetStatus(appId: AppId, status: UninstallStatus): Unit = {
    val _ = curator.create()
      .creatingParentContainersIfNeeded()
      .forPath(statusPath(appId),
        StorageEnvelope.encodeData(status))
  }

  private def markInProgress(appId: AppId): Unit = {
    Option(curator.checkExists().forPath(statusPath(appId))) match {
      case Some(thing) =>
        logger.info("When storing UninstallStatus for {}, found stale status of {} in ZK", appId, getStatus(appId))
        setStatus(appId, InProgress)
      case _ =>
        createAndSetStatus(appId, InProgress)
    }
  }

  private def deleteZkRecord(appId: AppId): Unit = {
    val _ = curator.delete().deletingChildrenIfNeeded().forPath(rootPath(appId))
  }

  private def markFailed(appId: AppId): Unit = {
    setStatus(appId, Failed)
  }

}
