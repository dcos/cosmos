package com.mesosphere.cosmos.io

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.io.Reader
import com.twitter.io.StreamIO
import com.twitter.util.Future
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import scala.collection.breakOut

final class ReaderInputStreamSpec extends FreeSpec with Matchers with PropertyChecks {
  import ReaderInputStreamSpec._

  "Test Reader to InputStream conversion" - {
    "for empty reader" in {
      ReaderInputStream(reader(Nil)).read() shouldBe -1
    }

    "for empty buffer reader" in {
      ReaderInputStream(reader(List(Buf.Empty))).read() shouldBe -1
    }

    "for 1 byte reader" in {
      val expected = Array(1.toByte)
      StreamIO.buffer(
        ReaderInputStream(
          reader(List(new Buf.ByteArray(expected, 0, expected.length)))
        )
      ).toByteArray() shouldBe expected
    }

    "for one buffer reader" in {
      val expected = "hello world".getBytes()
      StreamIO.buffer(
        ReaderInputStream(
          reader(List(new Buf.ByteArray(expected, 0, expected.length)))
        )
      ).toByteArray() shouldBe expected
    }

    "for many buffer reader" in {
      val expected = "hello world".getBytes()

      StreamIO.buffer(
        ReaderInputStream(
          reader(
            expected.grouped(2).map(array => new Buf.ByteArray(array, 0, array.length)).toSeq
          )
        )
      ).toByteArray() shouldBe expected
    }

    "for failed reader" in {
      intercept[IllegalArgumentException] {
        ReaderInputStream(FailedReader).read()
      }
      ()
    }

    "for a buffer then failed reader" in {
      val expected = "hello world".getBytes()
      val actual = Array.ofDim[Byte](expected.length)
      val inputStream = ReaderInputStream(
        Reader.concat(
          AsyncStream.fromSeq(
            Seq(
              reader(List(new Buf.ByteArray(expected, 0, expected.length))),
              FailedReader
            )
          )
        )
      )

      inputStream.read(actual, 0, actual.length)

      actual shouldBe expected
      intercept[IllegalArgumentException] {
        inputStream.read()
      }
      ()
    }

    "for all buffer" in {
      forAll { (buffers: List[Array[Byte]]) =>
        StreamIO.buffer(
          ReaderInputStream(
            reader(buffers.map(array => new Buf.ByteArray(array, 0, array.length)))
          )
        ).toByteArray() shouldBe buffers.flatten.toArray
      }
    }
  }
}

object ReaderInputStreamSpec {
  def reader(buffers: Seq[Buf]): Reader = {
    Reader.concat(AsyncStream.fromSeq(buffers).map(buffer => Reader.fromBuf(buffer)))
  }

  object FailedReader extends Reader {
    override def discard(): Unit = ()
    override def read(n: Int): Future[Option[Buf]] = {
      Future.exception(new IllegalArgumentException())
    }
  }
}
