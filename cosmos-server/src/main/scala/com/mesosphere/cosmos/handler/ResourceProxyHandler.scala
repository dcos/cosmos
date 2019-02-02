package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.error.Forbidden
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import io.lemonlabs.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Reader
import com.twitter.util.Future
import io.finch.Output
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.InputStream
import java.net.HttpURLConnection

final class ResourceProxyHandler private(
  packageCollection: PackageCollection
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
      /*
       * [DCOS-45428] We verify with both toString and toStringRaw method because:
       * 1. toString matches cases where the url was received with some URL encoded parameters (e.g.: %2B)
       *    and we need to use those characters as-is without performing any URL decoding.
       *    e.g.: https://<>/master%2Bdcos-ui-v2.37.0.tar.gz is Valid but
       *          https://<>/master+dcos-ui-v2.37.0.tar.gz is not a valid URL (Enforced by S3).
       *          So we need to retain that + is used as `%2B` and SHOULD NOT be decoded as `+`.
       * 2. toStringRaw matches the case where we need to perform proper URL decoding.
       *    e.g.: http://<>/sha256%3A5d591076/somefile.txt is valid URL that can be decoded as
       *          http://<>/sha256:5d591076/somefile.txt.
       */
      if (!urls.contains(uri.toString) && !urls.contains(uri.toStringRaw)) {
        throw Forbidden(ResourceProxyHandler.getClass.getSimpleName, Some(uri.toString)).exception
      }
    }.flatMap { _ =>
      HttpClient
        .fetchStream(
          uri
        ) { (responseData, conn) =>
          validateContentLength(uri, responseData.contentLength)
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
    packageCollection: PackageCollection
  )(implicit statsReceiver: StatsReceiver): ResourceProxyHandler = {
    implicit val handlerScope = statsReceiver.scope("resourceProxyHandler")
    new ResourceProxyHandler(packageCollection)(handlerScope)
  }

  def getFileNameFromUrl(url: Uri): Option[String] = {
    url.path.parts match {
      case _ :+ last if !last.isEmpty => Some(last)
      case _ :+ secondLast :+ last if last.isEmpty => Some(secondLast)
      case _ => None
    }
  }

  def validateContentLength(
    uri: Uri,
    contentLength: Option[Long]
  ): Unit = {
    contentLength match {
      case Some(length) =>
        length match {
          case l if l <= 0 =>
            throw GenericHttpError(uri = uri, clientStatus = HttpResponseStatus.BAD_GATEWAY).exception
          case _ => ()
        }
      case None =>
        throw GenericHttpError(uri = uri, clientStatus = HttpResponseStatus.BAD_GATEWAY).exception
    }
  }

}
