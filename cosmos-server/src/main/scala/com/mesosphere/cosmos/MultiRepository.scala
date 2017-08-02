package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository._
import com.mesosphere.universe
import com.mesosphere.universe.v4.model.PackageDefinition
import com.netaporter.uri.Uri
import com.twitter.util.Future

final class MultiRepository(
    packageRepositoryStorage: PackageSourcesStorage,
    universeClient: UniverseClient
)
    extends PackageCollection {

  @volatile private[this] var cachedRepositories =
    Map.empty[Uri, CosmosRepository]

  override def getPackagesByPackageName(
      packageName: String
  )(implicit session: RequestSession): Future[List[universe.v4.model.PackageDefinition]] = {
    /* Fold over all the results in order and ignore PackageNotFound errors.
     * We have found our answer when we find a PackageDefinition or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.getPackagesByPackageName(packageName)
        }
      }
    } map { packages =>
      packages.find(!_.isEmpty).getOrElse(
        throw PackageNotFound(packageName).exception
      )
    }
  }

  override def getPackageByPackageVersion(
      packageName: String,
      packageVersion: Option[universe.v3.model.Version]
  )(implicit session: RequestSession): Future[(universe.v4.model.PackageDefinition, Uri)] = {
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
              case CosmosException(PackageNotFound(_), _, _, _) => None
              case CosmosException(VersionNotFound(_, _), _, _, _) => None
            }
        }
      } map (_.flatten)
    } map { packages =>
      packages.headOption.getOrElse {
        packageVersion match {
          case Some(version) => throw VersionNotFound(packageName, version).exception
          case None => throw PackageNotFound(packageName).exception
        }
      }
    }
  }

  override def search(
      query: Option[String]
  )(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]] = {
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
            case (_, list) =>
              val (searchResult, _) = list.sortBy { case (_, index) => index
              }.head
              searchResult
          }
          .toList
      }
    }
  }

  override def upgradesTo(
    name: String,
    version: universe.v3.model.Version
  )(implicit session: RequestSession): Future[List[universe.v3.model.Version]] = {
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.upgradesTo(name, version)
        }
      }
    } map { versions => versions.flatten.toList }
  }

  override def downgradesTo(
    packageDefinition: PackageDefinition
  )(implicit session: RequestSession): Future[List[universe.v3.model.Version]] = {
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.downgradesTo(packageDefinition)
        }
      } map (_.flatten.toList.distinct)
    }
  }

  def getRepository(uri: Uri): Future[Option[CosmosRepository]] = {
    repositories().map { repositories =>
      repositories.find(_.repository.uri == uri)
    }
  }

  private[this] def repositories(): Future[List[CosmosRepository]] = {
    packageRepositoryStorage.readCache().map { repositories =>
      val oldRepositories: Map[Uri, CosmosRepository] = cachedRepositories
      val result = repositories.map { repository =>
        oldRepositories.getOrElse(
            repository.uri,
            CosmosRepository(repository, universeClient))
      }

      val newRepositories: Map[Uri, CosmosRepository] =
        result.map(cosmosRepository => (cosmosRepository.repository.uri, cosmosRepository)).toMap

      /* Note: This is an optimization. We always get the latest inventory of repository from
       * the storage.
       */
      cachedRepositories = newRepositories

      result
    }
  }
}
