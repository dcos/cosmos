package com.mesosphere.cosmos.storage

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.{MediaType, MediaTypeOps, MediaTypeSubType}
import com.mesosphere.cosmos.model.ZooKeeperStorageEnvelope
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe.common.ByteBuffers
import io.circe.Encoder
import io.circe.jawn.decode
import io.circe.syntax._

object Envelope {

  private[this] val envelopeMediaType = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.package.repository.repo-list", Some("json")),
    Map(
      "charset" -> "utf-8",
      "version" -> "v1"
    )
  )

  def decodeData(bytes: Array[Byte]): List[PackageRepository] = {
    decode[ZooKeeperStorageEnvelope](new String(bytes, StandardCharsets.UTF_8))
      .flatMap { envelope =>
        val contentType = envelope.metadata
          .get("Content-Type")
          .flatMap { s => MediaType.parse(s).toOption }

        contentType match {
          case Some(mt) if MediaTypeOps.compatible(envelopeMediaType, mt) =>
            val dataString: String = new String(
              ByteBuffers.getBytes(envelope.data),
              StandardCharsets.UTF_8)
            decode[List[PackageRepository]](dataString)
          case Some(mt) =>
            throw ZooKeeperStorageError(
              s"Error while trying to deserialize data. " +
                s"Expected Content-Type '${envelopeMediaType.show}' actual '${mt.show}'"
            )
          case None =>
            throw ZooKeeperStorageError(
              s"Error while trying to deserialize data. " +
                s"Content-Type not defined."
            )
        }
      } valueOr { err => throw CirceError(err) }
  }

  def toByteBuffer[A : Encoder](a: A): ByteBuffer = {
    ByteBuffer.wrap(a.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
  }

  def encodeEnvelope(data: ByteBuffer): Array[Byte] = {
    ZooKeeperStorageEnvelope(
      metadata = Map("Content-Type" -> envelopeMediaType.show),
      data = data
    ).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }
}
