package com.mesosphere.cosmos.handler

import com.google.common.io.ByteStreams
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.HttpClient.ResponseData
import com.mesosphere.cosmos.HttpClient.UnexpectedStatus
import com.mesosphere.cosmos.HttpClient.UriConnection
import com.mesosphere.cosmos.HttpClient.UriSyntax
import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.EndpointUriConnection
import com.mesosphere.cosmos.error.EndpointUriSyntax
import com.mesosphere.cosmos.error.Forbidden
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.ResourceTooLarge
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.netaporter.uri.Uri
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
  httpClient: HttpClient,
  contentLengthLimit: StorageUnit,
  statsReceiver: StatsReceiver
) {

  import ResourceProxyHandler._

  def apply(uri: Uri)(implicit
    session: RequestSession = RequestSession(None)
  ): Future[Output[Response]] = {

    packageCollection.allUrls().map { urls =>
      if (!urls.contains(uri.toString)) {
        throw Forbidden(ResourceProxyHandler.getClass.getCanonicalName, Some(uri.toString)).exception
      }
    }

    httpClient
      .fetch(uri, statsReceiver) { responseData =>
        // TODO proxy May want to factor out a method that can be tested separately
        val contentBytes = getContentBytes(uri, responseData, contentLengthLimit)
        val response = Response()
        response.content = Buf.ByteArray.Owned(contentBytes)
        response.contentType = responseData.contentType.show
        response.contentLength = contentBytes.length.toLong
        response
      }
      .map { result =>
        // TODO proxy Handle errors
        result match {
          case Right(response) => Output.payload(response)
          case Left(error) => error match {
            case UriSyntax(cause) =>
              // TODO better name
              throw CosmosException(EndpointUriSyntax(
                ResourceProxyHandler.getClass.getCanonicalName,
                uri,
                cause.getMessage),
                cause
              )
            case UriConnection(cause) =>
              throw CosmosException(EndpointUriConnection(
                ResourceProxyHandler.getClass.getCanonicalName,
                uri,
                cause.getMessage),
                cause
              )
            case UnexpectedStatus(clientStatus) =>
              throw GenericHttpError(
                uri = uri,
                clientStatus = Status.fromCode(clientStatus)
              ).exception(Status.InternalServerError)
          }
        }
      }
  }
}

object ResourceProxyHandler {

  // TODO proxy Determine what the actual limit should be; maybe use a flag?

  private val EofDetector: Array[Byte] = Array.ofDim(1)

  def apply(
    packageCollection: PackageCollection,
    contentLengthLimit: StorageUnit
  )(implicit statsReceiver: StatsReceiver): ResourceProxyHandler = {
    apply(packageCollection, HttpClient, contentLengthLimit, statsReceiver)
  }

  def apply(
    packageCollection: PackageCollection,
    httpClient: HttpClient,
    contentLengthLimit: StorageUnit,
    statsReceiver: StatsReceiver
  ): ResourceProxyHandler = {
    assert(contentLengthLimit.bytes > 0)
    val handlerScope = statsReceiver.scope("resourceProxyHandler")
    new ResourceProxyHandler(packageCollection, httpClient, contentLengthLimit, handlerScope)
  }

  def getContentBytes(
    uri: Uri,
    responseData: ResponseData,
    contentLengthLimit: StorageUnit
  ):Array[Byte] = {
    validateContentLength(uri, responseData.contentLength, contentLengthLimit)

    // TODO proxy Fail if data is too large
    // Allocate array of min(ContentLength size if defined, limit size - 1)
    // Buffer InputStream into array; if the end is reached but there's more data, fail
    // If the data runs out before the end of the array, truncate the array

    val length = responseData.contentLength match {
      case Some(len) => len.toInt
      case None => contentLengthLimit.bytes.toInt - 1
    }
    val contentBytes = Array.ofDim[Byte](length)
    val bytesRead = bufferFully(responseData.contentStream, contentBytes)

    if (responseData.contentLength.isDefined && bytesRead < contentBytes.length) {
      throw GenericHttpError(uri = uri, clientStatus = Status.InternalServerError).exception
    }

    bufferFully(responseData.contentStream, EofDetector) match {
      case 0 if responseData.contentLength.isEmpty =>
        contentBytes.dropRight(contentBytes.length - bytesRead)
      case x if x > 0 =>
        throw convertToCosmosError(uri, responseData, contentLengthLimit).exception
      case _ => contentBytes
    }
  }

  private def bufferFully(is: InputStream, buffer: Array[Byte]): Int = {
    ByteStreams.read(is, buffer, 0, buffer.length)
  }

  private def convertToCosmosError(
    uri: Uri,
    responseData: ResponseData,
    contentLengthLimit: StorageUnit
  ):CosmosError = {
    responseData.contentLength match {
      case Some(_) => GenericHttpError(
        uri = uri,
        clientStatus = Status.InternalServerError
      )
      case None => ResourceTooLarge (
        contentLength = None,
        contentLengthLimit.bytes
      )
    }
  }

  private def validateContentLength(
    uri: Uri,
    contentLength: Option[Long],
    contentLengthLimit: StorageUnit
  ):Unit = {
    contentLength match {
      case l if l.exists(_ >= contentLengthLimit.bytes) =>
        throw ResourceTooLarge(contentLength, contentLengthLimit.bytes).exception
      case l if l.exists(_ <= 0) =>
        throw GenericHttpError(uri = uri, clientStatus = Status.InternalServerError).exception
      case _ => ()
    }
  }

}
