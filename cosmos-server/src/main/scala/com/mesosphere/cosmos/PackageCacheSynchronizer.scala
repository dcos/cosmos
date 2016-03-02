package com.mesosphere.cosmos

import java.net.{MalformedURLException, UnknownHostException}
import java.nio.file._
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

import com.mesosphere.cosmos.model.{PackageRepository, SearchResult}
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe.{PackageDetailsVersion, PackageFiles, ReleaseVersion, UniverseIndexEntry}
import com.netaporter.uri.Uri
import com.twitter.concurrent.AsyncMutex
import com.twitter.io.{Charsets, Files => TwitterFiles}
import com.twitter.util.{Future, Try}

private[cosmos] final class PackageCacheSynchronizer(repository: PackageRepository, universeDir: Path)
  extends Repository with AutoCloseable {

  import PackageCacheSynchronizer._

  override def uri: Uri = repository.uri

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[PackageDetailsVersion]
  ): Future[PackageFiles] = {
    mostRecentCache().flatMap(_.getPackageByPackageVersion(packageName, packageVersion))
  }

  override def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles] = {
    mostRecentCache().flatMap(_.getPackageByReleaseVersion(packageName, releaseVersion))
  }

  override def getPackageIndex(packageName: String): Future[UniverseIndexEntry] = {
    mostRecentCache().flatMap(_.getPackageIndex(packageName))
  }

  override def search(queryOpt: Option[String]): Future[List[SearchResult]] = {
    mostRecentCache().flatMap(_.search(queryOpt))
  }

  override def close(): Unit = {
    // Note: The order is very important!!

    val symlinkBundlePath = universeDir.resolve(base64(repository.uri))

    // Remember the old bundle path so we can delete it later
    val bundlePath = readSymbolicLink(symlinkBundlePath)

    // Delete the bundle directory
    bundlePath.foreach { p =>
      //TODO: This needs to be more robust to handle potential lingering symlinks
      Files.delete(symlinkBundlePath)  // work around until https://github.com/mesosphere/cosmos/issues/246 is fixed
      // Delete the symbolic link
      TwitterFiles.delete(p.toFile)
    }
  }

  // This mutex serializes updates to the local package cache
  private[this] val updateMutex = new AsyncMutex()

  private[this] val lastModified = new AtomicReference(LocalDateTime.MIN)

  private[this] val cache = new AtomicReference[UniversePackageCache]()

  private[this] def mostRecentCache(): Future[UniversePackageCache] = {
    updateMutex.acquireAndRun {
      // TODO: How often we check should be configurable
      if (lastModified.get().plusMinutes(1).isBefore(LocalDateTime.now())) {
        updateUniverseCache()
          .onSuccess { newCache =>
            lastModified.set(LocalDateTime.now())
            cache.set(newCache)
          }
      } else {
        Future.value(cache.get())
      }
    }
  }

  private[this] def updateUniverseCache(): Future[UniversePackageCache] = {
    Future(repository.uri.toURI.toURL.openStream())
      .handle {
        case e @ (_: IllegalArgumentException | _: MalformedURLException | _: UnknownHostException) =>
          throw new InvalidRepositoryUri(repository, e)
      }
      .map { bundleStream =>
        try {
          val path = extractBundle(
            new ZipInputStream(bundleStream),
            repository.uri,
            universeDir
          )

          // TODO: The path will always be the same, since it contains a symlink; eventually
          // it might be better to use a different path each time, so that clients see a
          // consistent snapshot of the repo, instead of one that could change underneath them
          UniversePackageCache(repository.uri, path, FilesystemPackageMetadataStore)
        } finally {
          bundleStream.close()
        }
      }
  }

}

object PackageCacheSynchronizer {

  private def base64(universeBundle: Uri): String = {
    Base64.getUrlEncoder().encodeToString(
      universeBundle.toString.getBytes(Charsets.Utf8)
    )
  }

  private def extractBundle(
    bundle: ZipInputStream,
    universeBundle: Uri,
    universeDir: Path
  ): Path = {

    val entries = Iterator.continually(Option(bundle.getNextEntry()))
      .takeWhile(_.isDefined)
      .flatten

    val tempDir = Files.createTempDirectory(universeDir, "repository")
    tempDir.toFile.deleteOnExit()

    try {
      // Copy all of the entry to a temporary folder
      entries.foreach { entry =>
        val entryPath = Paths.get(entry.getName)

        // Skip the root directory
        if (entryPath.getNameCount > 1) {
          val tempEntryPath = tempDir
            .resolve(entryPath.subpath(1, entryPath.getNameCount))

          if (entry.isDirectory) {
            Files.createDirectory(tempEntryPath)
          } else {
            Files.copy(bundle, tempEntryPath)
          }
        }
      }

      // Move the symblic to the temp directory to the actual universe directory...
      val bundlePath = universeDir.resolve(base64(universeBundle))
      val newBundlePath = universeDir.resolve(base64(universeBundle) + ".new")

      // Remember the old bundle path so we can delete it later
      val oldBundlePath = readSymbolicLink(bundlePath)

      // Create new symbolic link to the new bundle directory
      Files.createSymbolicLink(newBundlePath, tempDir)

      // Atomic move of the temporary directory
      val path = Files.move(
        newBundlePath,
        bundlePath,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )

      // Delete the old bundle directory
      oldBundlePath.foreach { p => TwitterFiles.delete(p.toFile) }

      path
    } catch {
      case e: Exception =>
        // Only delete directory on failures because we want to keep it around on success.
        TwitterFiles.delete(tempDir.toFile)
        throw e
    }
  }

  private def readSymbolicLink(path: Path): Option[Path] = {
    Try(Files.readSymbolicLink(path))
      .map { path =>
        Some(path)
      }
      .handle {
        case e: NoSuchFileException =>
          // this is okay, we expect the link to not be there the first time
          None
      }
      .get
  }

}
