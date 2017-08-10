package com.mesosphere.universe.common.circe

import com.mesosphere.universe.common.ByteBuffers
import com.netaporter.uri.Uri
import com.twitter.util.Duration
import io.circe.Encoder
import io.circe.syntax._
import java.nio.ByteBuffer
import java.util.Base64

object Encoders {

  implicit val encodeUri: Encoder[Uri] = Encoder.instance(_.toString.asJson)

  implicit val encodeByteBuffer: Encoder[ByteBuffer] = Encoder.instance { bb =>
    Base64.getEncoder.encodeToString(ByteBuffers.getBytes(bb)).asJson
  }

  implicit val encodeDurationToSeconds: Encoder[Duration] = Encoder.instance { duration =>
    s"${duration.inSeconds} seconds".asJson
  }

}
