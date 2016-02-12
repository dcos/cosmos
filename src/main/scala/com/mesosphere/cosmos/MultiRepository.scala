package com.mesosphere.cosmos

import java.nio.file.Path

import com.netaporter.uri.Uri
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try

import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe.PackageDetailsVersion
import com.mesosphere.universe.PackageFiles
import com.mesosphere.universe.UniverseIndexEntry

final class MultiRepository (
  packageRepositoryStorage: PackageSourcesStorage,
  universeDir: Path
) extends PackageCollection {
  @volatile private[this] var cachedRepositories = Map.empty[Uri, UniversePackageCache]

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    /* Fold over all the results in order and ignore PackageNotFound and VersionNotFound errors.
     * We have found our answer when we find a PackageFile or a generic exception.
     */
    repositories.flatMap { repositories =>
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
    repositories.flatMap { repositories =>
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

  override def search(query: Option[String]): Future[List[UniverseIndexEntry]] = {
    repositories.flatMap { repositories =>
      Future.collect {
        repositories.map { repository =>
          repository.search(query)
        }
      } map(_.toList.flatten)
    }
  }

  def getRepository(uri: Uri): Future[Repository] = {
   repositories.map { repositories =>
      repositories.find { repository =>
        repository.universeBundle == uri
      } getOrElse(throw RepositoryNotFound(uri))
    }
  }

  private def repositories(): Future[List[UniversePackageCache]] = {
    packageRepositoryStorage.read().map { repositories =>
      val oldRepositories: Map[Uri, UniversePackageCache] = cachedRepositories
      val result = repositories.map { repository =>
        oldRepositories.get(repository.uri).getOrElse(
          UniversePackageCache(repository.uri, universeDir)
        )
      }

      val newRepositories: Map[Uri, UniversePackageCache] = result.map(repository =>
          (repository.universeBundle, repository)
      ).toMap

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
