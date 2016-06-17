package com.mesosphere.cosmos

import java.nio.file.Path

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.repository._
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future

// TODO (version): Update this to match the signature of V3MultiRepository
final class MultiRepository(
    packageRepositoryStorage: PackageSourcesStorage,
    universeDir: Path,
    universeClient: UniverseClient
)
    extends PackageCollection {
  @volatile private[this] var cachedRepositories =
    Map.empty[Uri, UniversePackageCache]

  override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[universe.v2.model.PackageDetailsVersion]
  ): Future[universe.v2.model.PackageFiles] = {
    /* Fold over all the results in order and ignore PackageNotFound and VersionNotFound errors.
     * We have found our answer when we find a PackageFile or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository
            .getPackageByPackageVersion(packageName, packageVersion)
            .map(Some(_))
            .handle {
              case PackageNotFound(_) => None
              case VersionNotFound(_, _) => None
            }
        }
      } map (_.flatten)
    } map { packageFiles =>
      packageFiles.headOption.getOrElse {
        packageVersion match {
          case Some(version) => throw VersionNotFound(packageName, version)
          case None => throw PackageNotFound(packageName)
        }
      }
    }
  }

  override def getPackageIndex(
      packageName: String): Future[universe.v2.model.UniverseIndexEntry] = {
    /* Fold over all the results in order and ignore PackageNotFound errors.
     * We have found our answer when we find a UniverseIndexEntry or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.getPackageIndex(packageName).map(Some(_)).handle {
            case PackageNotFound(_) => None
          }
        }
      } map (_.flatten)
    } map { packageFiles =>
      packageFiles.headOption.getOrElse(throw PackageNotFound(packageName))
    }
  }

  override def search(
      query: Option[String]): Future[List[rpc.v1.model.SearchResult]] = {
    repositories().flatMap { repositories =>
      val searches = repositories.zipWithIndex.map {
        case (repository, repositoryIndex) =>
          repository.search(query).map { results =>
            results.map(_ -> repositoryIndex)
          }
      }
      val fSearchResults = Future.collect(searches).map(_.toList.flatten)
      fSearchResults.map { results =>
        results
          .groupBy(_._1.name)
          .map {
            case (name, list) =>
              list.sortBy(_._2).head._1
          }
          .toList
      }
    }
  }

  def getRepository(uri: Uri): Future[Option[CosmosRepository]] = {
    repositories().map { repositories =>
      repositories.find(_.universeBundle == uri)
    }
  }

  private def repositories(): Future[List[UniversePackageCache]] = {
    packageRepositoryStorage.readCache().map { repositories =>
      val oldRepositories: Map[Uri, UniversePackageCache] = cachedRepositories
      val result = repositories.map { repository =>
        oldRepositories.getOrElse(
            repository.uri,
            UniversePackageCache(repository, universeDir, universeClient))
      }

      val newRepositories: Map[Uri, UniversePackageCache] =
        result.map(repository => (repository.universeBundle, repository)).toMap

      // Clean any repository that is not used anymore
      (oldRepositories -- newRepositories.keys).foreach {
        case (_, value) =>
          value.close()
      }

      /* Note: This is an optimization. We always get the latest inventory of repository from
       * the storage.
       */
      cachedRepositories = newRepositories

      result
    }
  }
}

// TODO (version): Delete this
final class V3MultiRepository(
    packageRepositoryStorage: PackageSourcesStorage,
    universeClient: V3UniverseClient,
    releaseVersion: universe.v3.model.DcosReleaseVersion
)
    extends V3PackageCollection {

  @volatile private[this] var cachedRepositories =
    Map.empty[Uri, V3CosmosRepository]

  override def getPackagesByPackageName(
      packageName: String
  ): Future[List[internal.model.PackageDefinition]] = {
    /* Fold over all the results in order and ignore PackageNotFound errors.
     * We have found our answer when we find a PackageDefinition or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.getPackagesByPackageName(packageName).map(Some(_)).handle {
            case PackageNotFound(_) => None
          }
        }
      } map (_.flatten)
    } map { packages =>
      packages.headOption.getOrElse(throw PackageNotFound(packageName))
    }
  }

  override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): Future[(internal.model.PackageDefinition, Uri)] = {
    /* Fold over all the results in order and ignore PackageNotFound and VersionNotFound errors.
     * We have found our answer when we find a PackageDefinition or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository
            .getPackageByPackageVersion(packageName, packageVersion)
            .map(Some(_))
            .handle {
              case PackageNotFound(_) => None
              case VersionNotFound(_, _) => None
            }
        }
      } map (_.flatten)
    } map { packages =>
      packages.headOption.getOrElse {
        packageVersion match {
          case Some(version) => throw VersionNotFound(packageName, version.as[universe.v2.model.PackageDetailsVersion])
          case None => throw PackageNotFound(packageName)
        }
      }
    }
  }

  override def search(
      query: Option[String]): Future[List[rpc.v1.model.V3SearchResult]] = {
    repositories().flatMap { repositories =>
      val searches = repositories.zipWithIndex.map {
        case (repository, repositoryIndex) =>
          repository.search(query).map { results =>
            results.map(_ -> repositoryIndex)
          }
      }

      val fSearchResults = Future.collect(searches).map(_.toList.flatten)

      fSearchResults.map { results =>
        results
          .groupBy {
            case (searchResult, _) => searchResult.name
          }
          .map {
            case (name, list) =>
              val (searchResult, _) = list.sortBy { case (_, index) => index
              }.head
              searchResult
          }
          .toList
      }
    }
  }

  private[this] def repositories(): Future[List[V3CosmosRepository]] = {
    packageRepositoryStorage.readCache().map { repositories =>
      val oldRepositories: Map[Uri, V3CosmosRepository] = cachedRepositories
      val result = repositories.map { repository =>
        oldRepositories.getOrElse(
            repository.uri,
            V3CosmosRepository(repository, universeClient, releaseVersion))
      }

      val newRepositories: Map[Uri, V3CosmosRepository] =
        result.map(cosmosRepository => (cosmosRepository.repository.uri, cosmosRepository)).toMap

      /* Note: This is an optimization. We always get the latest inventory of repository from
       * the storage.
       */
      cachedRepositories = newRepositories

      result
    }
  }
}
