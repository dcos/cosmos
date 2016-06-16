package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import scala.util.matching.Regex

/** A repository of packages that can be installed on DCOS. */
trait CosmosRepository extends PackageCollection {

  def repository: rpc.v1.model.PackageRepository

  def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v2.model.ReleaseVersion
  ): Future[universe.v2.model.PackageFiles]
}

// TODO (version): Rename to CosmosRepository
/** A repository of packages that can be installed on DCOS. */
trait V3CosmosRepository extends V3PackageCollection {

  def repository: rpc.v1.model.PackageRepository

  def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  ): Future[internal.model.PackageDefinition]
}

object V3CosmosRepository {
  def apply(repository: rpc.v1.model.PackageRepository,
            universeClient: V3UniverseClient,
            releaseVersion: universe.v3.model.DcosReleaseVersion)
    : V3CosmosRepository = {
    new DefaultCosmosRepository(repository, universeClient, releaseVersion)
  }
}

final class DefaultCosmosRepository(
    override val repository: rpc.v1.model.PackageRepository,
    universeClient: V3UniverseClient,
    releaseVersion: universe.v3.model.DcosReleaseVersion
)
    extends V3CosmosRepository {

  private[this] val lastRepository = new AtomicReference(
      Option.empty[(internal.model.CosmosInternalRepository, LocalDateTime)])

  override def getPackageByReleaseVersion(
      packageName: String,
      releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion
  ): Future[internal.model.PackageDefinition] = {
    // TODO(version): Need to make sure that the packages are sorted correctly.
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
      packageVersion: Option[universe.v3.model.PackageDefinition.Version])
    : Future[(internal.model.PackageDefinition, Uri)] = {
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
      packageName: String): Future[List[internal.model.PackageDefinition]] = {
    synchronizedUpdate().map { internalRepository =>
      internalRepository.packages.filter(_.name == packageName)
    }
  }

  override def search(
      query: Option[String]): Future[List[rpc.v1.model.V3SearchResult]] = {
    val predicate =
      query.map { value =>
        if (value.contains("*")) {
          searchRegex(createRegex(value))
        } else {
          searchString(value.toLowerCase())
        }
      } getOrElse { (_: rpc.v1.model.V3SearchResult) =>
        true
      }

    synchronizedUpdate().map { internalRepository =>
      internalRepository.packages
        .foldLeft(Map.empty[String, rpc.v1.model.V3SearchResult]) {
          (state, pkg) =>
            val searchResult = state
              .get(pkg.name)
              .getOrElse(rpc.v1.model.V3SearchResult(
                      pkg.name,
                      pkg.version,
                      Map((pkg.version, pkg.releaseVersion)),
                      pkg.description,
                      pkg.framework.getOrElse(false),
                      pkg.tags,
                      pkg.selected,
                      pkg.resource.flatMap(_.images)))

            state + ((pkg.name, searchResult))
        }
        .toList
        .map { case (_, searchResult) => searchResult }
        .filter(predicate)
    }
  }

  // TODO(version): make sure that the list in packages is sorted
  private[this] def synchronizedUpdate(
      ): Future[internal.model.CosmosInternalRepository] = {
    lastRepository.get() match {
      case Some((internalRepository, lastModified)) =>
        if (lastModified.plusMinutes(1).isBefore(LocalDateTime.now())) {
          universeClient(repository.uri, releaseVersion).onSuccess {
            newRepository =>
              lastRepository.set(Some((newRepository, LocalDateTime.now())))
          }
        } else {
          Future(internalRepository)
        }

      case None =>
        universeClient(repository.uri, releaseVersion).onSuccess {
          newRepository =>
            lastRepository.set(Some((newRepository, LocalDateTime.now())))
        }
    }
  }

  private[this] def createRegex(query: String): Regex = {
    s"""^${query.replaceAll("\\*", ".*")}$$""".r
  }

  private[this] def searchRegex(
      regex: Regex): rpc.v1.model.V3SearchResult => Boolean = { pkg =>
    regex.findFirstIn(pkg.name).isDefined ||
    regex.findFirstIn(pkg.description).isDefined ||
    pkg.tags.exists(tag => regex.findFirstIn(tag.value).isDefined)
  }

  private[this] def searchString(
      query: String): rpc.v1.model.V3SearchResult => Boolean = { pkg =>
    pkg.name.toLowerCase().contains(query) ||
    pkg.description.toLowerCase().contains(query) ||
    pkg.tags.exists(tag => tag.value.toLowerCase().contains(query))
  }
}