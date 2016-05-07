package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.http.RequestSession

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

object TestUtil {

  def deleteRecursively(path: Path): Unit = {
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

    val _ = Files.walkFileTree(path, visitor)
  }

  implicit val Anonymous = RequestSession(None)

}
