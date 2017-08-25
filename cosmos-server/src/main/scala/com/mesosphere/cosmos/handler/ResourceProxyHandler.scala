package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.error.ResourceTooLarge
import com.netaporter.uri.Uri
import com.twitter.conversions.storage._
import com.twitter.finagle.http.Response
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Buf
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.StorageUnit
import io.finch.Output

final class ResourceProxyHandler private(
  contentLengthLimit: StorageUnit,
  statsReceiver: StatsReceiver
) {

  def apply(uri: Uri): Future[Output[Response]] = {
    HttpClient
      .fetch(uri, statsReceiver) { responseData =>
        val response = Response()
        if (responseData.contentLength.exists(_ >= contentLengthLimit.bytes)) {
          throw ResourceTooLarge(responseData.contentLength, contentLengthLimit.bytes).exception
        }

        // TODO proxy Fail if data is too large
        // Allocate array of ContentLength size, or of the limit size
        // Buffer InputStream into array; if the end is reached but there's more data, fail
        // If the data runs out before the end of the array, truncate the array
        val contentBytes = StreamIO.buffer(responseData.contentStream).toByteArray
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

  def apply(contentLengthLimit: StorageUnit, statsReceiver: StatsReceiver): ResourceProxyHandler = {
    new ResourceProxyHandler(contentLengthLimit, statsReceiver.scope("resourceProxyHandler"))
  }

}
