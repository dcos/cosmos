package com.mesosphere.cosmos.handler

import com.google.common.io.ByteStreams
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.HttpClient.ResponseData
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
import com.twitter.io.Buf
import com.twitter.util.Future
import com.twitter.util.StorageUnit
import io.finch.Output
import java.io.InputStream

final class ResourceProxyHandler private(
  packageCollection: PackageCollection,
  contentLengthLimit: StorageUnit
)(implicit statsReceiver: StatsReceiver) {
  import ResourceProxyHandler._

  def apply(data : (Uri, RequestSession)): Future[Output[Response]] = {
    val uri = data._1
    implicit val session = data._2

    packageCollection.allUrls().map { urls =>
      if (!urls.contains(uri.toString)) {
        throw Forbidden(ResourceProxyHandler.getClass.getSimpleName, Some(uri.toString)).exception
      }
    }.flatMap { _ =>
      HttpClient
        .fetch(
          uri
        ){ responseData =>
          val contentBytes = getContentBytes(uri, responseData, contentLengthLimit)
          val response = Response()
          response.content = Buf.ByteArray.Owned(contentBytes)
          response.contentType = responseData.contentType.show
          response.contentLength = contentBytes.length.toLong
          getFileNameFromUrl(uri) match {
            case Some(filename) => response.headerMap.add(Fields.ContentDisposition,
              s"""attachment; filename="$filename"""")
            case _ =>
          }
          response
        }
        .map(Output.payload(_))
    }
  }
}

object ResourceProxyHandler {

  private val EofDetector: Array[Byte] = Array.ofDim(1)

  def apply(
    packageCollection: PackageCollection,
    contentLengthLimit: StorageUnit
  )(implicit statsReceiver: StatsReceiver): ResourceProxyHandler = {
    if(contentLengthLimit.bytes <= 0) {
      throw InvalidContentLengthLimit(contentLengthLimit).exception
    }

    implicit val handlerScope = statsReceiver.scope("resourceProxyHandler")
    new ResourceProxyHandler(packageCollection, contentLengthLimit)(handlerScope)
  }

  def getContentBytes(
    uri: Uri,
    responseData: ResponseData,
    contentLengthLimit: StorageUnit
  ):Array[Byte] = {
    validateContentLength(uri, responseData.contentLength, contentLengthLimit)

    // Allocate array of ContentLength size
    // Buffer InputStream into array; if the end is reached but there's more data, fail
    // If the data runs out before the end of the array, fail

    val Some(length) = responseData.contentLength.map(_.toInt)
    val contentBytes = Array.ofDim[Byte](length)
    val bytesRead = bufferFully(responseData.contentStream, contentBytes)
    if (bytesRead < contentBytes.length) {
      throw GenericHttpError(uri = uri, clientStatus = Status.BadGateway).exception
    }

    bufferFully(responseData.contentStream, EofDetector) match {
      case 0 if responseData.contentLength.isEmpty =>
        contentBytes.dropRight(contentBytes.length - bytesRead)
      case x if x > 0 =>
        throw GenericHttpError(uri = uri, clientStatus = Status.BadGateway).exception
      case _ => contentBytes
    }
  }

  def getFileNameFromUrl(url: Uri): Option[String] = {
    url.pathParts match {
      case _ :+ last if !last.part.isEmpty => Some(last.part)
      case _ :+ secondLast :+ last if last.part.isEmpty => Some(secondLast.part)
      case _ => None
    }
  }

  private def bufferFully(is: InputStream, buffer: Array[Byte]): Int = {
    ByteStreams.read(is, buffer, 0, buffer.length)
  }

  private def validateContentLength(
    uri: Uri,
    contentLength: Option[Long],
    contentLengthLimit: StorageUnit
  ):Unit = {
    contentLength match {
      case Some(length) =>
        length match {
          case l if l >= contentLengthLimit.bytes =>
            throw ResourceTooLarge(contentLength, contentLengthLimit.bytes).exception
          case l if l <= 0 =>
            throw GenericHttpError(uri = uri, clientStatus = Status.BadGateway).exception
          case _ => ()
        }
      case None =>
        throw GenericHttpError(uri = uri, clientStatus = Status.BadGateway).exception
    }
  }

}
