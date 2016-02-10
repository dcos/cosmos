package com.mesosphere.cosmos.repository

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.{MediaType, MediaTypeOps, MediaTypeSubType}
import com.mesosphere.cosmos.model.{PackageRepository, ZooKeeperStorageEnvelope}
import com.mesosphere.cosmos.{ByteBuffers, CirceError, ZooKeeperStorageError}
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.util.Future
import io.circe.Encoder
import io.circe.parse._
import io.circe.syntax._
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.KeeperException.NoNodeException

private[cosmos] final class ZooKeeperStorage(zkClient: CuratorFramework, defaultUniverseUri: Uri)
  (implicit statsReceiver: StatsReceiver = NullStatsReceiver)
  extends PackageSourcesStorage {

  import ZooKeeperStorage._

  private[this] val stats = statsReceiver.scope("zkStorage")
  private[this] val envelopeMediaType = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.package.repository.repo-list", Some("json")),
    Some(Map(
      "charset" -> "utf-8",
      "version" -> "v1"
    ))
  )
  private[this] def encodeEnvelope(data: ByteBuffer): Array[Byte] = ZooKeeperStorageEnvelope(
    metadata = Map("Content-Type" -> envelopeMediaType.show),
    data = data
  ).asJson.noSpaces.getBytes(StandardCharsets.UTF_8)

  private[this] val DefaultSources: List[PackageRepository] =
    List(PackageRepository("Universe", defaultUniverseUri))

  // TODO cruhland: Tests for this class
  // TODO cruhland: Use a FuturePool

  def read(): Future[List[PackageRepository]] = {
    Stat.timeFuture(stats.stat("read")) {
      Future(zkClient.getData.forPath(PackageRepositoriesPath))
    }
      .map { bytes =>
        decode[ZooKeeperStorageEnvelope](new String(bytes, StandardCharsets.UTF_8))
          .flatMap { envelope =>
            val contentType = envelope.metadata
              .get("Content-Type")
              .flatMap { s => MediaType.parse(s).toOption }

            contentType match {
              case Some(mt) if MediaTypeOps.compatible(envelopeMediaType, mt) =>
                val dataString: String = new String(ByteBuffers.getBytes(envelope.data), StandardCharsets.UTF_8)
                decode[List[PackageRepository]](dataString)
              case Some(mt) =>
                throw new ZooKeeperStorageError(
                  s"Error while trying to deserialize data. " +
                    s"Expected Content-Type '${envelopeMediaType.show}' actual '${mt.show}'"
                )
              case None =>
                throw new ZooKeeperStorageError(
                  s"Error while trying to deserialize data. " +
                    s"Content-Type not defined."
                )
            }
          } valueOr { err => throw new CirceError(err) }
      }
      .rescue {
        case e: NoNodeException =>
          write(DefaultSources)
      }
  }

  def write(sources: List[PackageRepository]): Future[List[PackageRepository]] = {
    val encodedEnvelope = encodeEnvelope(toByteBuffer(sources))
    Stat.timeFuture(stats.stat("write")) {
      Future(zkClient.setData().forPath(PackageRepositoriesPath, encodedEnvelope))
        .handle {
          case e: NoNodeException =>
            zkClient.create.creatingParentsIfNeeded.forPath(PackageRepositoriesPath, encodedEnvelope)
        }
    }
      .flatMap { _  =>
        // TODO (jsancio): We are ignoring Stat information for now. Issue #192
        read()
      }
  }

  @inline
  private[this] def toByteBuffer[A : Encoder](a: A): ByteBuffer = {
    ByteBuffer.wrap(a.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
  }
}

private object ZooKeeperStorage {

  private val PackageRepositoriesPath: String = "/package/repositories"

}
