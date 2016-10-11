package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.util.Future
import com.twitter.common.util.{LowResClock, Clock}
import com.twitter.common.quantity.Amount
import com.twitter.common.quantity.Time

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import scala.util.matching.Regex

/** A repository of packages that can be installed on DCOS. */
trait CosmosRepository extends PackageCollection {

  def repository: rpc.v1.model.PackageRepository

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  )(implicit session: RequestSession): Future[universe.v3.model.PackageDefinition]

}

object CosmosRepository {
  def apply(repository: rpc.v1.model.PackageRepository,
            universeClient: UniverseClient,
            clock: Clock = new LowResClock(Amount.of(1L, Time.SECONDS))
            )
    : CosmosRepository = {
    new DefaultCosmosRepository(repository, universeClient, clock)
  }
}

final class DefaultCosmosRepository(
    override val repository: rpc.v1.model.PackageRepository,
    universeClient: UniverseClient,
    clock: Clock = new LowResClock(Amount.of(1L, Time.SECONDS))
)
  extends CosmosRepository {
  private[this] val lastRepository = new AtomicReference(
      Option.empty[(universe.v3.model.Repository, Long)])

  override def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  )(implicit session: RequestSession): Future[universe.v3.model.PackageDefinition] = {
    synchronizedUpdate().map { internalRepository =>
      internalRepository.packages.find { pkg =>
        pkg.name == packageName && pkg.releaseVersion == releaseVersion
      } getOrElse {
        throw PackageNotFound(packageName)
      }
    }
  }

  override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  )(implicit session: RequestSession): Future[(universe.v3.model.PackageDefinition, Uri)] = {
    synchronizedUpdate().map { internalRepository =>
      val ns = internalRepository.packages.filter { pkg =>
        pkg.name == packageName
      }
      val vs = packageVersion match {
        case Some(ver) => ns.find(_.version == ver)
        case _ => ns.headOption
      }
      (packageVersion, ns, vs) match {
        case (Some(ver), _ :: _ , None) => throw VersionNotFound(packageName, ver)
        case (_, _ , None)              => throw PackageNotFound(packageName)
        case (_,_, Some(pkg))           => (pkg, repository.uri)
      }
    }
  }

  override def getPackagesByPackageName(
      packageName: String
  )(implicit session: RequestSession): Future[List[universe.v3.model.PackageDefinition]] = {
    synchronizedUpdate().map { internalRepository =>
      internalRepository.packages.filter(_.name == packageName)
    }
  }

  override def search(
      query: Option[String]
  )(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]] = {
    val predicate =
      query.map { value =>
        if (value.contains("*")) {
          searchRegex(createRegex(value))
        } else {
          searchString(value.toLowerCase())
        }
      } getOrElse { (_: rpc.v1.model.SearchResult) =>
        true
      }

    synchronizedUpdate().map { internalRepository =>
      internalRepository.packages
        .foldLeft(Map.empty[String, rpc.v1.model.SearchResult]) {
          (state, pkg) =>
            val searchResult = state
              .getOrElse(pkg.name, rpc.v1.model.SearchResult(
                pkg.name,
                pkg.version,
                Map((pkg.version, pkg.releaseVersion)),
                pkg.description,
                pkg.framework.getOrElse(false),
                pkg.tags,
                pkg.selected,
                pkg.images
              ))

            val releaseVersion = searchResult.versions.get(pkg.version).map { releaseVersion =>
              import universe.v3.model.PackageDefinition.ReleaseVersion
              implicitly[Ordering[ReleaseVersion]].max(releaseVersion, pkg.releaseVersion)
            } getOrElse {
              pkg.releaseVersion
            }

            val newSearchResult = searchResult.copy(
              versions=searchResult.versions + ((pkg.version, releaseVersion))
            )

            state + ((pkg.name, newSearchResult))
        }
        .toList
        .map { case (_, searchResult) => searchResult }
        .filter(predicate)
    }
  }

  private[this] def synchronizedUpdate()(
    implicit session: RequestSession
  ): Future[universe.v3.model.Repository] = {
    lastRepository.get() match {
      case Some((internalRepository, lastModified)) =>
        val now = TimeUnit.MILLISECONDS.toSeconds(clock.nowMillis)
        val lastSec = TimeUnit.MILLISECONDS.toSeconds(lastModified)
        val refetch = lastSec + TimeUnit.MINUTES.toSeconds(1)
        if (refetch < now || lastSec > now) {
          universeClient(repository).onSuccess {
            newRepository =>
              lastRepository.set(Some((newRepository, clock.nowMillis)))
          }
        } else {
          Future(internalRepository)
        }

      case None =>
        universeClient(repository).onSuccess {
          newRepository =>
            lastRepository.set(Some((newRepository, clock.nowMillis)))
        }
    }
  }

  private[this] def safePattern(query: String): String = {
      query.split("\\*", -1).map{
        case "" => ""
        case v => Pattern.quote(v)
      }.mkString(".*")
  }
  private[repository] def createRegex(query: String): Regex = {
    s"""^${safePattern(query)}$$""".r
  }

  private[this] def searchRegex(
      regex: Regex): rpc.v1.model.SearchResult => Boolean = { pkg =>
    regex.findFirstIn(pkg.name).isDefined ||
    regex.findFirstIn(pkg.description).isDefined ||
    pkg.tags.exists(tag => regex.findFirstIn(tag.value).isDefined)
  }

  private[this] def searchString(
      query: String): rpc.v1.model.SearchResult => Boolean = { pkg =>
    pkg.name.toLowerCase().contains(query) ||
    pkg.description.toLowerCase().contains(query) ||
    pkg.tags.exists(tag => tag.value.toLowerCase().contains(query))
  }
}
