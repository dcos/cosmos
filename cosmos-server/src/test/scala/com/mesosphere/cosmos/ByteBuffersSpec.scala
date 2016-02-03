package com.mesosphere.cosmos

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util

import org.scalatest.FreeSpec

class ByteBuffersSpec extends FreeSpec {

  "ByteBuffers.toBytes(ByteBuffer) should" - {
    "work for an array backed ByteBuffer" in {
      val bytes = "hello".getBytes(StandardCharsets.UTF_8)
      val bb = ByteBuffer.wrap(bytes)
      val actual = ByteBuffers.getBytes(bb)
      assert(util.Arrays.equals(bytes, actual))
    }

    "work for a non-array backed ByteBuffer" in {
      val bytes = "hello".getBytes(StandardCharsets.UTF_8)
      val bb = ByteBuffer.allocateDirect(bytes.size)
      bytes.foreach(bb.put)
      bb.rewind() // rewind the position back to the beginning after having written
      val actual = ByteBuffers.getBytes(bb)
      assert(util.Arrays.equals(bytes, actual))
    }

    "check read index bounds" in {
      val bytes = "hello".getBytes(StandardCharsets.UTF_8)
      val bb = ByteBuffer.allocateDirect(bytes.size)
      bytes.foreach(bb.put)
      try {
        val _ = ByteBuffers.getBytes(bb)
      } catch {
        case ioobe: IndexOutOfBoundsException =>
          assertResult("5 > 0")(ioobe.getMessage)
      }
    }
  }

}
