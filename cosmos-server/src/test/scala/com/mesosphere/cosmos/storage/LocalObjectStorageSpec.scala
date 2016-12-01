package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.test.TestUtil
import com.twitter.io.StreamIO
import com.twitter.util.Await
import java.io.ByteArrayInputStream
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class LocalObjectStorageSpec extends FreeSpec with PropertyChecks {

  import LocalObjectStorageSpec._

  "read() on a nonexistent file returns Future(None)" in {
    TestUtil.withLocalObjectStorage { localStorage =>
      forAll (genPath) { path =>
        whenever (isValidPath(path)) {
          assertResult(None) {
            Await.result(localStorage.read(path))
          }
        }
      }
    }
  }

  "read() cannot observe partial writes to a file" in {
    forAll (genPath, arbitrary[Array[Byte]]) { (path, dataToWrite) =>
      whenever (isValidPath(path)) {
        TestUtil.withLocalObjectStorage { localStorage =>
          val writeStream = new ByteArrayInputStream(dataToWrite)
          val contentLength = dataToWrite.length.toLong

          val writeOp = localStorage.write(path, writeStream, contentLength)
          val readOp = TestUtil.eventualFuture(() => localStorage.read(path))
          val (_, (_, readStream)) = Await.result(writeOp.join(readOp))
          val dataFromRead = StreamIO.buffer(readStream).toByteArray

          assertResult(contentLength)(dataFromRead.length.toLong)
          assertResult(dataToWrite)(dataFromRead)
        }
      }
    }
  }

}

object LocalObjectStorageSpec {

  val genPath: Gen[String] = {
    val maxSegments = 10
    val maxSegmentLength = 10
    val genSegment = Gen.choose(1, maxSegmentLength)
      .flatMap(Gen.buildableOfN[String, Char](_, Gen.alphaNumChar))

    for {
      numSegments <- Gen.choose(1, maxSegments)
      segments <- Gen.listOfN(numSegments, genSegment)
      path = segments.mkString("/")
      if isValidPath(path)
    } yield path
  }

  def isValidPath(s: String): Boolean = s.nonEmpty && !s.forall(_ == '/') && !s.startsWith("/")

}
