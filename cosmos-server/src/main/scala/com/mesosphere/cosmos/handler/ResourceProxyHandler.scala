package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.error.Forbidden
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.InvalidContentLengthLimit
import com.mesosphere.cosmos.error.ResourceTooLarge
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Reader
import com.twitter.util.Future
import com.twitter.util.StorageUnit
import io.finch.Output
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.InputStream
import java.net.HttpURLConnection

final class ResourceProxyHandler private(
  packageCollection: PackageCollection,
  contentLengthLimit: StorageUnit
)(implicit statsReceiver: StatsReceiver) {

  import ResourceProxyHandler._

  class ConnectionClosingInputStream(
    connection: HttpURLConnection,
    inputStream: InputStream
  ) extends InputStream {
    def read(): Int = inputStream.read()

    override def close(): Unit = {
      inputStream.close()
      connection.disconnect()
    }
  }

  def apply(data: (Uri, RequestSession)): Future[Output[Response]] = {
    val uri = data._1
    implicit val session = data._2

    packageCollection.allUrls().map { urls =>
      if (!urls.contains(uri.toString)) {
        throw Forbidden(ResourceProxyHandler.getClass.getSimpleName, Some(uri.toString)).exception
      }
    }.flatMap { _ =>
      HttpClient
        .fetchStream(
          uri
        ) { (responseData, conn) =>
          validateContentLength(uri, responseData.contentLength, contentLengthLimit)
          val response = Response(
            com.twitter.finagle.http.Version.Http11,
            Status.Ok,
            Reader.fromStream(new ConnectionClosingInputStream(conn, responseData.contentStream))
          )
          response.contentType = responseData.contentType.show
          response.headerMap.add(Fields.TransferEncoding, "chunked")
          for (filename <- getFileNameFromUrl(uri)) {
            response.headerMap.add(Fields.ContentDisposition,
              s"""attachment; filename="$filename"""")
          }
          Output.payload(response)
        }
    }
  }
}

object ResourceProxyHandler {

  def apply(
    packageCollection: PackageCollection,
    contentLengthLimit: StorageUnit
  )(implicit statsReceiver: StatsReceiver): ResourceProxyHandler = {
    if (contentLengthLimit.bytes <= 0) {
      throw InvalidContentLengthLimit(contentLengthLimit).exception
    }

    implicit val handlerScope = statsReceiver.scope("resourceProxyHandler")
    new ResourceProxyHandler(packageCollection, contentLengthLimit)(handlerScope)
  }

  def getFileNameFromUrl(url: Uri): Option[String] = {
    url.pathParts match {
      case _ :+ last if !last.part.isEmpty => Some(last.part)
      case _ :+ secondLast :+ last if last.part.isEmpty => Some(secondLast.part)
      case _ => None
    }
  }

  def validateContentLength(
    uri: Uri,
    contentLength: Option[Long],
    contentLengthLimit: StorageUnit
  ): Unit = {
    contentLength match {
      case Some(length) =>
        length match {
          case l if l >= contentLengthLimit.bytes =>
            throw ResourceTooLarge(contentLength, contentLengthLimit.bytes).exception
          case l if l <= 0 =>
            throw GenericHttpError(uri = uri, clientStatus = HttpResponseStatus.BAD_GATEWAY).exception
          case _ => ()
        }
      case None =>
        throw GenericHttpError(uri = uri, clientStatus = HttpResponseStatus.BAD_GATEWAY).exception
    }
  }

}
