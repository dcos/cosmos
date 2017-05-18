package com.mesosphere.cosmos.janitor

import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.locks.InterProcessMutex
import org.slf4j.Logger


/**
  * Allows a cosmos instance to acquire a lock on a particular uninstall.
  */
trait UninstallLock {
  /**
    * Checks if this uninstall is already locked by this JVM
    * @param appId
    * @return True if this app is locked by this JVM
    */
  def isLockedByThisProcess(appId: AppId): Boolean
  def lock(appId: AppId): Boolean
  def unlock(appId: AppId): Unit
}

/**
  * Implements UninstallLock using Curator (ZK)
  */
final class CuratorUninstallLock(curator: CuratorFramework) extends UninstallLock {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)
  val locks = scala.collection.mutable.Map[AppId, InterProcessMutex]()

  val lockExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
    .setDaemon(true)
    .setNameFormat("uninstall-lock-thread")
    .build())

  private def getLockPath(appId: AppId): String = {
    Paths.get(SdkJanitor.UninstallFolder, appId.toString, "lock").toString
  }

  private def getMutex(appId: AppId): InterProcessMutex = {
    new InterProcessMutex(curator, getLockPath(appId))
  }

  private def lockOnThread(mutex: InterProcessMutex): Boolean = {
    val lockCallable = new Callable[Boolean] {
      override def call(): Boolean = {
        mutex.acquire(CuratorUninstallLock.AcquireTimeout, TimeUnit.MILLISECONDS)
      }
    }
   lockExecutor.submit(lockCallable).get()
  }

  private def unlockOnThread(mutex: InterProcessMutex): Unit = {
    val unlockCallable = new Callable[Unit] {
      override def call(): Unit = {
        mutex.release()
      }
    }
    lockExecutor.submit(unlockCallable).get()
  }

  override def isLockedByThisProcess(appId: AppId): Boolean = {
    locks.get(appId) match {
      case Some(mutex) => mutex.isAcquiredInThisProcess
      case _ => false
    }
  }

  override def lock(appId: AppId): Boolean = {
    logger.info("Attempting to acquire lock for {}", getLockPath(appId))
    locks.get(appId) match {
      case Some(mutex) =>
        logger.info("Lock {} already exists. Attempting to acquire it.", getLockPath(appId))
        lockOnThread(mutex)
      case _ =>
        logger.info("Lock {} does not already exist. Creating it and acquiring it", getLockPath(appId))
        val mutex = getMutex(appId)
        lockOnThread(mutex) match {
          case true =>
            logger.info("Acquired lock for {}", getLockPath(appId))
            locks.put(appId, mutex)
            true
          case false =>
            logger.info("Lock for {} not acquired. Already owned", getLockPath(appId))
            false
        }
    }
  }

  override def unlock(appId: AppId): Unit = {
    logger.info("Attempting to release lock for {}", getLockPath(appId))
    locks.get(appId) match {
      case Some(mutex) =>
        unlockOnThread(mutex)
        logger.info("Released lock for {}", getLockPath(appId))
      case _ => throw new IllegalMonitorStateException("You do not own the lock for " + getLockPath(appId))
    }
  }
}

object CuratorUninstallLock {
  val AcquireTimeout = 0L
}
