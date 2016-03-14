package com.mesosphere.cosmos

import java.nio.ByteBuffer

object ByteBuffers {

  def getBytes(bb: ByteBuffer): Array[Byte] = {
    if (bb.hasArray){
      bb.array()
    } else {
      //the index of the first byte in the backing byte array of this ByteBuffer.
      //if there isn't a backing byte array, then this throws
      val position = bb.position()

      val remaining = bb.remaining()
      if (position > remaining) {
        throw new IndexOutOfBoundsException(s"$position > $remaining")
      } else {
        val dest = new Array[Byte](remaining)
        bb.get(dest, position, remaining)
        dest
      }
    }
  }

}
