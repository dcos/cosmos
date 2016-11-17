package com.mesosphere.cosmos.io

import com.twitter.io.Buf
import com.twitter.io.Reader
import com.twitter.util.Await
import java.io.InputStream
import scala.math.min

final class ReaderInputStream private (reader: Reader) extends InputStream {
  import ReaderInputStream._

  @volatile
  private[this] var state = Option((Array.empty[Byte], 0))

  override def read(): Int = {
    defaultCases.orElse(singleRead)(state)
  }

  override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
    if (offset < 0 || length < 0 || length > bytes.length - offset) {
      throw new IndexOutOfBoundsException()
    }

    defaultCases.orElse(bufferRead(bytes, offset, length))(state)
  }

  override def available(): Int = {
    val count = for {
      (buffer, position) <- state
    } yield buffer.length - position

    count.getOrElse(0)
  }

  override def close(): Unit = reader.discard()

  private[this] def readNextBuffer(): Unit = {
    state = Await.result {
      reader.read(maxBufferSize).map { result =>
        result.map(buffer => (Buf.ByteArray.Owned.extract(buffer), 0))
      }
    }
  }

  private[this] val defaultCases: PartialFunction[Option[(Array[Byte], Int)], Int] = {
    case None =>
      -1
  }

  private[this] def bufferRead(
    bytes: Array[Byte],
    offset: Int,
    length: Int
  ): PartialFunction[Option[(Array[Byte], Int)], Int] = {
    case Some((buffer, position)) if position < buffer.length =>
      val size = min(buffer.length - position, length)
      if (size <= 0) {
        0
      } else {
        Array.copy(buffer, position, bytes, offset, size)

        // Record the new state
        state = Some((buffer, position + size))

        size
      }
    case _ =>
      readNextBuffer()

      read(bytes, offset, length)
  }

  private[this] val singleRead: PartialFunction[Option[(Array[Byte], Int)], Int] = {
    case Some((buffer, position)) if position < buffer.length =>
      val byte = buffer(position)

      // Record the new state
      state = Some((buffer, position + 1))

      // hint bytes in Java are the two's complement of the integer value
      byte & 0xff
    case _ =>
      readNextBuffer()

      read()
  }
}

object ReaderInputStream {
  private val maxBufferSize = 1024 * 1024 * 4 // 4MB

  def apply(reader: Reader): InputStream = new ReaderInputStream(reader)
}
