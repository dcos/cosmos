package com.mesosphere.cosmos.storage

import java.util.concurrent.atomic.AtomicReference

import scala.collection.concurrent.TrieMap
import com.mesosphere.cosmos.ConcurrentPackageUpdateDuringPublish
import com.mesosphere.cosmos.converter.Common.BundleToPackage
import com.mesosphere.universe.v3.model._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

final class InMemoryPackageStorage extends PackageStorage {
  private[this] val packages = TrieMap[String, AtomicReference[Vector[PackageDefinition]]]()

  override def getRepository: Future[Repository] = Future.value {
      Repository(
        packages.toList.flatMap(_._2.get())
      )
  }

  override def putPackageBundle(packageBundle: BundleDefinition): Future[Unit] = Future.value {
    val empty = new AtomicReference[Vector[PackageDefinition]](Vector())
    val named = packages.putIfAbsent(name(packageBundle), empty) match {
      case Some(p) => p
      case _ => empty
    }
    val oldp = named.get
    val newp = oldp :+ (packageBundle, PackageDefinition.ReleaseVersion(oldp.size).get).as[PackageDefinition]

    if (!named.compareAndSet(oldp, newp)) {
      throw ConcurrentPackageUpdateDuringPublish()
    }
  }

  private[this] def name(pkg: BundleDefinition): String =
    pkg match {
      case v2: V2Bundle => v2.name
      case v3: V3Bundle => v3.name
    }
}
