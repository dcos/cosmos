package com.mesosphere.cosmos.storage

import com.netaporter.uri.Uri
import com.twitter.util.{ Future, Promise }
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.ZooKeeperStorageError
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._

import io.circe.Encoder
import io.circe.jawn.decode
import io.circe.syntax._

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{ BackgroundCallback, CuratorEvent, CuratorEventType }

import org.apache.zookeeper.KeeperException

import java.nio.charset.StandardCharsets

final class PackageQueue(zkClient: CuratorFramework) extends
  PackageQueueAdder with PackageQueueReader with PackageQueueRemover
{

  import PackageQueueHelpers._

  override def add(
    pkg: PackageCoordinate,
    content: PackageQueueContents
  ): Future[PackageAddResult] = {

    val promise = Promise[PackageAddResult]()
    val pkgPath = s"$packageQueueBase/${pkg.as[String]}"

    zkClient.create.creatingParentsIfNeeded.inBackground(
      new AddHandler(promise)
    ).forPath(
      pkgPath,
      Envelope.encodeData(content)
    )

    promise

  }

  private[this] final class AddHandler(
    promise: Promise[PackageAddResult]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.CREATE) {
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            promise.setValue(Created)
          case KeeperException.Code.NODEEXISTS =>
            promise.setValue(AlreadyExists)
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      } else {
        logger.error("Repository storage create callback called for incorrect event: {}", event)
      }
    }
  }


}

private object PackageQueueHelpers {

  val packageQueueBase = "/package/packageQueue"

}
