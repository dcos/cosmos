package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future

import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import scala.util.matching.Regex

/** A repository of packages that can be installed on DCOS. */
trait CosmosRepository extends PackageCollection {

  def repository: rpc.v1.model.PackageRepository

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  )(implicit session: RequestSession): Future[internal.model.PackageDefinition]

}

object CosmosRepository {
  def apply(repository: rpc.v1.model.PackageRepository,
            universeClient: UniverseClient)
    : CosmosRepository = {
    new DefaultCosmosRepository(repository, universeClient)
  }
}

final class DefaultCosmosRepository(
    override val repository: rpc.v1.model.PackageRepository,
    universeClient: UniverseClient
)
  extends CosmosRepository {

  private[this] val lastRepository = new AtomicReference(
      Option.empty[(internal.model.CosmosInternalRepository, LocalDateTime)])

  override def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  )(implicit session: RequestSession): Future[internal.model.PackageDefinition] = {
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
  )(implicit session: RequestSession): Future[(internal.model.PackageDefinition, Uri)] = {
    synchronizedUpdate().map { internalRepository =>
      internalRepository.packages.find { pkg =>
        pkg.name == packageName &&
        packageVersion.map(_ == pkg.version).getOrElse(true)
      } map { pkg =>
        (pkg, repository.uri)
      } getOrElse {
        throw PackageNotFound(packageName)
      }
    }
  }

  override def getPackagesByPackageName(
      packageName: String
  )(implicit session: RequestSession): Future[List[internal.model.PackageDefinition]] = {
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
              .get(pkg.name)
              .getOrElse(rpc.v1.model.SearchResult(
                      pkg.name,
                      pkg.version,
                      Map((pkg.version, pkg.releaseVersion)),
                      pkg.description,
                      pkg.framework,
                      pkg.tags,
                      Some(pkg.selected),
                      pkg.resource.flatMap(_.images)))

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
  ): Future[internal.model.CosmosInternalRepository] = {
    lastRepository.get() match {
      case Some((internalRepository, lastModified)) =>
        if (lastModified.plusMinutes(1).isBefore(LocalDateTime.now())) {
          universeClient(repository).onSuccess {
            newRepository =>
              lastRepository.set(Some((newRepository, LocalDateTime.now())))
          }
        } else {
          Future(internalRepository)
        }

      case None =>
        universeClient(repository).onSuccess {
          newRepository =>
            lastRepository.set(Some((newRepository, LocalDateTime.now())))
        }
    }
  }

  private[this] def createRegex(query: String): Regex = {
    s"""^${query.split("\\*").map(Pattern.quote(_)).mkString(".*")}$$""".r
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
