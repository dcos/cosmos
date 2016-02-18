package com.mesosphere.cosmos

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import com.mesosphere.cosmos.model.PackageRepository
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** Utilities for integration tests. In an object so that they must be referenced explicitly or
  * imported, *not* mixed in.
  */
private object IntegrationTests {

  private[cosmos] final def withTempDirectory(f: Path => Unit): Unit = {
    val tempDir = Files.createTempDirectory("cosmos")
    try { f(tempDir) } finally {
      val visitor = new SimpleFileVisitor[Path] {

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
          Option(e) match {
            case Some(failure) => throw failure
            case _ =>
              Files.delete(dir)
              FileVisitResult.CONTINUE
          }
        }

      }

      val _ = Files.walkFileTree(tempDir, visitor)
    }
  }

  private[cosmos] def constUniverse(uri: Uri): PackageSourcesStorage = new PackageSourcesStorage {

    val read: Future[List[PackageRepository]] = {
      Future.value(List(PackageRepository("Universe", uri)))
    }

    val readCache: Future[List[PackageRepository]] = {
      Future.value(List(PackageRepository("Universe", uri)))
    }

    def add(index: Int, packageRepository: PackageRepository): Future[List[PackageRepository]] = {
      Future.exception(new UnsupportedOperationException)
    }

    def delete(name: Option[String], uri: Option[Uri]): Future[List[PackageRepository]] = {
      Future.exception(new UnsupportedOperationException)
    }

  }

}
