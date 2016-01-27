package com.mesosphere.cosmos

import com.twitter.util._

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException

object IntegrationHelpers {

  def withTempDirectory[A](f: Path => A): A = {
      val tempDir = Files.createTempDirectory("cosmos")
      Try(f(tempDir)).ensure {
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
      }.get()
  }

}
