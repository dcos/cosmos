package com.mesosphere.cosmos.handler

import com.google.common.io.ByteStreams
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.error.ResourceTooLarge
import com.netaporter.uri.Uri
import com.twitter.conversions.storage._
import com.twitter.finagle.http.Response
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Buf
import com.twitter.util.Future
import com.twitter.util.StorageUnit
import io.finch.Output
import java.io.InputStream

final class ResourceProxyHandler private(
  httpClient: HttpClient,
  contentLengthLimit: StorageUnit,
  statsReceiver: StatsReceiver
) {

  import ResourceProxyHandler._

  def apply(uri: Uri): Future[Output[Response]] = {
    httpClient
      .fetch(uri, statsReceiver) { responseData =>
        // TODO proxy May want to factor out a method that can be tested separately
        val response = Response()
        if (responseData.contentLength.exists(_ >= contentLengthLimit.bytes)) {
          throw ResourceTooLarge(responseData.contentLength, contentLengthLimit.bytes).exception
        }

        // TODO proxy Fail if data is too large
        // Allocate array of ContentLength size, or of the limit size - 1
        // Buffer InputStream into array; if the end is reached but there's more data, fail
        // If the data runs out before the end of the array, truncate the array
        val contentBytes = Array.ofDim[Byte](contentLengthLimit.bytes.toInt - 1)
        bufferFully(responseData.contentStream, contentBytes)

        if (bufferFully(responseData.contentStream, EofDetector) > 0) {
          throw ResourceTooLarge(contentLength = None, contentLengthLimit.bytes).exception
        }

        response.content = Buf.ByteArray.Owned(contentBytes)
        response.contentType = responseData.contentType.show
        response
      }
      .map { result =>
        // TODO proxy Handle errors
        Output.payload(result.right.get)
      }
  }

}

object ResourceProxyHandler {

  // TODO proxy Determine what the actual limit should be; maybe use a flag?
  val DefaultContentLengthLimit: StorageUnit = 5.megabytes

  private val EofDetector: Array[Byte] = Array.ofDim(1)

  def apply()(implicit statsReceiver: StatsReceiver): ResourceProxyHandler = {
    apply(HttpClient, DefaultContentLengthLimit, statsReceiver)
  }

  def apply(
    httpClient: HttpClient,
    contentLengthLimit: StorageUnit,
    statsReceiver: StatsReceiver
  ): ResourceProxyHandler = {
    assert(contentLengthLimit.bytes > 0)
    val handlerScope = statsReceiver.scope("resourceProxyHandler")
    new ResourceProxyHandler(httpClient, contentLengthLimit, handlerScope)
  }

  private def bufferFully(is: InputStream, buffer: Array[Byte]): Int = {
    ByteStreams.read(is, buffer, 0, buffer.length)
  }

}
