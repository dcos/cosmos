package com.mesosphere.cosmos

import java.nio.file.Path

import com.mesosphere.cosmos.model.SearchResult
import com.mesosphere.cosmos.repository.{PackageCollection, PackageSourcesStorage, Repository}
import com.mesosphere.universe.{PackageDetailsVersion, PackageFiles, UniverseIndexEntry}
import com.netaporter.uri.Uri
import com.twitter.util.Future

final class MultiRepository (
  packageRepositoryStorage: PackageSourcesStorage,
  universeDir: Path
) extends PackageCollection {
  @volatile private[this] var cachedRepositories = Map.empty[Uri, Repository with AutoCloseable]

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    /* Fold over all the results in order and ignore PackageNotFound and VersionNotFound errors.
     * We have found our answer when we find a PackageFile or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.getPackageByPackageVersion(packageName, packageVersion)
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

  override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
    /* Fold over all the results in order and ignore PackageNotFound errors.
     * We have found our answer when we find a UniverseIndexEntry or a generic exception.
     */
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.getPackageIndex(packageName)
            .map(Some(_))
            .handle {
              case PackageNotFound(_) => None
            }
        }
      } map (_.flatten)
    } map { packageFiles =>
      packageFiles.headOption.getOrElse(throw PackageNotFound(packageName))
    }
  }

  override def search(query: Option[String]): Future[List[SearchResult]] = {
    repositories().flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.search(query)
        }
      } map(_.toList.flatten)
    }
  }

  def getRepository(uri: Uri): Future[Option[Repository]] = {
   repositories().map { repositories =>
      repositories.find(_.uri == uri)
    }
  }

  private def repositories(): Future[List[Repository]] = {
    packageRepositoryStorage.readCache().map { repositories =>
      val oldRepositories = cachedRepositories
      val result = repositories.map { repository =>
        oldRepositories.getOrElse(repository.uri, new PackageCacheSynchronizer(repository, universeDir))
      }

      val newRepositories = result.map(repository => (repository.uri, repository)).toMap

      // Clean any repository that is not used anymore
      (oldRepositories -- newRepositories.keys).foreach { case (_, value) =>
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
