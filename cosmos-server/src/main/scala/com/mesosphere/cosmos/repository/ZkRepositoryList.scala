package com.mesosphere.cosmos.repository

import cats.data.Ior
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.repository.DefaultRepositories._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.storage.DistributedGlobal
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.util._
import org.apache.curator.framework.CuratorFramework

private[cosmos] final class ZkRepositoryList(
  zkClient: CuratorFramework
)(implicit
  statsReceiver: StatsReceiver = NullStatsReceiver
) extends PackageSourcesStorage {
  import ZkRepositoryList._

  private[this] val stats = statsReceiver.scope("zkStorage")
  private[this] val DefaultRepos: List[PackageRepository] = DefaultRepositories().getOrElse(Nil)
  private[this] val global = DistributedGlobal(zkClient, ZkRepositoryList.PackageRepositoriesPath, DefaultRepos)

  override def read(): Future[List[PackageRepository]] = {
    Stat.timeFuture(stats.stat("read")) {
      global.get.map(_._1)
    }
  }

  override def readCache(): Future[List[PackageRepository]] = {
    Stat.timeFuture(stats.stat("readCache")) {
      global.getCached.map(_._1)
    }
  }

  override def add(
    index: Option[Int],
    packageRepository: PackageRepository
  ): Future[List[PackageRepository]] = {
    Stat.timeFuture(stats.stat("add")) {
      global.get.flatMap { case (data, version) =>
          global.set(addToList(index, packageRepository, data), version).map(_._1)
      }
    }
  }

  override def delete(nameOrUri: Ior[String, Uri]): Future[List[PackageRepository]] = {
    Future.value(getPredicate(nameOrUri)).flatMap { predicate =>
      Stat.timeFuture(stats.stat("delete")) {
        global.get.flatMap {
          case (originalData, version) =>
            val updatedData = originalData.filterNot(predicate)
            if (originalData.size == updatedData.size) {
              throw new RepositoryNotPresent(nameOrUri)
            }
            global.set(updatedData, version).map(_._1)
        }
      }
    }
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
