package com.mesosphere.cosmos.repository

import cats.data.Ior
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.repository.DefaultRepositories._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.util._
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{BackgroundCallback, CuratorEvent, CuratorEventType}
import org.apache.curator.framework.recipes.cache.NodeCache
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.data.{Stat => ZooKeeperStat}

private[cosmos] final class ZkRepositoryList(
  zkClient: CuratorFramework
)(implicit
  statsReceiver: StatsReceiver = NullStatsReceiver
) extends PackageSourcesStorage {

  import ZkRepositoryList._

  private[this] val caching = new NodeCache(zkClient, ZkRepositoryList.PackageRepositoriesPath)
  caching.start()

  private[this] val stats = statsReceiver.scope("zkStorage")

  private[this] val DefaultRepos: List[PackageRepository] = DefaultRepositories().getOrElse(Nil)

  override def read(): Future[List[PackageRepository]] = {
    Stat.timeFuture(stats.stat("read")) {
      readFromZooKeeper.flatMap {
        case Some((_, bytes)) =>
          Future(Envelope.decodeData[List[PackageRepository]](bytes))
        case None =>
          create(DefaultRepos)
      }
    }
  }

  override def readCache(): Future[List[PackageRepository]] = {
    val readCacheStats = stats.scope("readCache")
    Stat.timeFuture(readCacheStats.stat("call")) {
      readFromCache.flatMap {
        case Some((_, bytes)) =>
          readCacheStats.counter("hit").incr
          Future(Envelope.decodeData[List[PackageRepository]](bytes))
        case None =>
          readCacheStats.counter("miss").incr
          read()
      }
    }
  }

  override def add(
    index: Option[Int],
    packageRepository: PackageRepository
  ): Future[List[PackageRepository]] = {
    Stat.timeFuture(stats.stat("add")) {
      readFromZooKeeper.flatMap {
        case Some((stat, bytes)) =>
          write(stat, addToList(index, packageRepository, Envelope.decodeData[List[PackageRepository]](bytes)))

        case None =>
          create(addToList(index, packageRepository, DefaultRepos))
      }
    }
  }

  override def delete(nameOrUri: Ior[String, Uri]): Future[List[PackageRepository]] = {
    Future.value(getPredicate(nameOrUri)).flatMap { predicate =>
      Stat.timeFuture(stats.stat("delete")) {
        readFromZooKeeper.flatMap {
          case Some((stat, bytes)) =>
            val originalData = Envelope.decodeData[List[PackageRepository]](bytes)
            val updatedData = originalData.filterNot(predicate)
            if (originalData.size == updatedData.size) {
              throw new RepositoryNotPresent(nameOrUri)
            }

            write(stat, updatedData)
          case None =>
            create(DefaultRepos.filterNot(predicate))
        }
      }
    }
  }

  private[this] def create(
    repositories: List[PackageRepository]
  ): Future[List[PackageRepository]] = {
    val promise = Promise[List[PackageRepository]]()

    zkClient.create.creatingParentsIfNeeded.inBackground(
      new CreateHandler(promise, repositories)
    ).forPath(
      ZkRepositoryList.PackageRepositoriesPath,
      Envelope.encodeData(repositories)
    )

    promise
  }

  private[this] def write(
    stat: ZooKeeperStat,
    repositories: List[PackageRepository]
  ): Future[List[PackageRepository]] = {
    val promise = Promise[List[PackageRepository]]()

    zkClient.setData().withVersion(stat.getVersion).inBackground(
      new WriteHandler(promise, repositories)
    ).forPath(
      ZkRepositoryList.PackageRepositoriesPath,
      Envelope.encodeData(repositories)
    )

    promise
  }

  private[this] def readFromCache: Future[Option[(ZooKeeperStat, Array[Byte])]] = {
    Future {
      Option(caching.getCurrentData()).map { data =>
        (data.getStat, data.getData)
      }
    }
  }

  private[this] def readFromZooKeeper: Future[Option[(ZooKeeperStat, Array[Byte])]] = {
    val promise = Promise[Option[(ZooKeeperStat, Array[Byte])]]()

    zkClient.getData().inBackground(
      new ReadHandler(promise)
    ).forPath(
      ZkRepositoryList.PackageRepositoriesPath
    )

    promise
  }

  private[this] def addToList(
    index: Option[Int],
    elem: PackageRepository,
    list: List[PackageRepository]
  ): List[PackageRepository] = {
    val duplicateName = list.find(_.name == elem.name).map(_.name)
    val duplicateUri = list.find(_.uri == elem.uri).map(_.uri)
    val duplicates = (duplicateName, duplicateUri) match {
      case (Some(n), Some(u)) => Some(Ior.Both(n, u))
      case (Some(n), _) => Some(Ior.Left(n))
      case (_, Some(u)) => Some(Ior.Right(u))
      case _ => None
    }
    duplicates.foreach(d => throw new RepositoryAlreadyPresent(d))

    index match {
      case Some(i) if 0 <= i && i < list.size =>
        val (leftSources, rightSources) = list.splitAt(i)
        leftSources ++ (elem :: rightSources)
      case Some(i) if i == list.size =>
        list :+ elem
      case None =>
        list :+ elem
      case Some(i) =>
        throw new RepositoryAddIndexOutOfBounds(i, list.size - 1)
    }
  }
}

private object ZkRepositoryList {
  private val PackageRepositoriesPath: String = "/package/repositories"

  private[cosmos] def getPredicate(nameOrUri: Ior[String, Uri]): PackageRepository => Boolean = {
    def namePredicate(n: String) = (repo: PackageRepository) => repo.name == n
    def uriPredicate(u: Uri) = (repo: PackageRepository) => repo.uri == u

    nameOrUri match {
      case Ior.Both(n, u) => (repo: PackageRepository) => namePredicate(n)(repo) && uriPredicate(u)(repo)
      case Ior.Left(n) => namePredicate(n)
      case Ior.Right(u) => uriPredicate(u)
    }
  }

}

private final class WriteHandler(
  promise: Promise[List[PackageRepository]],
  repositories: List[PackageRepository]
) extends  BackgroundCallback {
  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def processResult(client: CuratorFramework, event: CuratorEvent): Unit ={
    if (event.getType == CuratorEventType.SET_DATA) {
      val code = KeeperException.Code.get(event.getResultCode)
      if (code == KeeperException.Code.OK) {
        promise.setValue(repositories)
      } else {
        val exception = if (code == KeeperException.Code.BADVERSION) {
          // BADVERSION is expected so let's display a friendlier error
          ConcurrentAccess(KeeperException.create(code, event.getPath))
        } else {
          KeeperException.create(code, event.getPath)
        }

        promise.setException(exception)
      }
    } else {
      logger.error("Repository storage write callback called for incorrect event: {}", event)
    }
  }
}

private final class CreateHandler(
  promise: Promise[List[PackageRepository]],
  repositories: List[PackageRepository]
) extends  BackgroundCallback {
  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def processResult(client: CuratorFramework, event: CuratorEvent): Unit ={
    if (event.getType == CuratorEventType.CREATE) {
      val code = KeeperException.Code.get(event.getResultCode)
      if (code == KeeperException.Code.OK) {
        promise.setValue(repositories)
      } else {
        val exception = if (code == KeeperException.Code.NODEEXISTS) {
          // NODEEXISTS is expected so let's display a friendlier error
          ConcurrentAccess(KeeperException.create(code, event.getPath))
        } else {
          KeeperException.create(code, event.getPath)
        }

        promise.setException(exception)
      }
    } else {
      logger.error("Repository storage create callback called for incorrect event: {}", event)
    }
  }
}

private final class ReadHandler(
  promise: Promise[Option[(ZooKeeperStat, Array[Byte])]]
) extends  BackgroundCallback {
  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def processResult(client: CuratorFramework, event: CuratorEvent): Unit ={
    if (event.getType == CuratorEventType.GET_DATA) {
      val code = KeeperException.Code.get(event.getResultCode)
      if (code == KeeperException.Code.OK) {
        promise.setValue(Some((event.getStat, event.getData)))
      } else if (code == KeeperException.Code.NONODE) {
        promise.setValue(None)
      } else {
        promise.setException(KeeperException.create(code, event.getPath))
      }
    } else {
      logger.error("Repository storage read callback called for incorrect event: {}", event)
    }
  }
}
