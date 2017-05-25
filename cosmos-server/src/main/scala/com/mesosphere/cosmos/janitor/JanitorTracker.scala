package com.mesosphere.cosmos.janitor

import java.nio.file.Paths
import com.mesosphere.cosmos.janitor.SdkJanitor._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.slf4j.Logger

trait Tracker {
  def startUninstall(appId: AppId): UninstallClaim
  def failUninstall(appId: AppId, reasons: List[String]): Unit
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
      UninstallClaimDenied
    } else {
      // Does some other JVM already own the lock?
      if (lock.lock(appId)) {
        logger.info("Acquired uninstall lock for app: {}", appId)
        UninstallClaimGranted
      } else {
        logger.info("Failed to acquire uninstall lock for app: {}", appId)
        UninstallClaimDenied
      }
    }
  }


  override def completeUninstall(appId: AppId): Unit = {
    lock.unlock(appId)
    deleteZkRecord(appId)
  }

  override def failUninstall(appId: AppId, reasons: List[String]): Unit = {
    lock.unlock(appId)
  }

  private def deleteZkRecord(appId: AppId): Unit = {
    val _ = curator.delete().deletingChildrenIfNeeded().forPath(rootPath(appId))
  }

  private def rootPath(appId: AppId): String = {
    // uninstalls/<name>
    Paths.get(UninstallFolder, appId.toString).toString
  }
}
