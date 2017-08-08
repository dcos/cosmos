package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Response
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Buf
import com.twitter.io.StreamIO
import com.twitter.util.Future
import io.finch.Output

final class ResourceProxyHandler private(statsReceiver: StatsReceiver) {

  def apply(uri: Uri): Future[Output[Response]] = {
    HttpClient
      .fetch(uri, statsReceiver) { (contentType, contentStream) =>
        val response = Response()
        // TODO proxy Fail if data is too large
        response.content = Buf.ByteArray.Owned(StreamIO.buffer(contentStream).toByteArray)
        response.contentType = contentType.show
        response
      }
      .map { result =>
        // TODO proxy Handle errors
        Output.payload(result.right.get)
      }
  }

}

object ResourceProxyHandler {

  def apply(statsReceiver: StatsReceiver): ResourceProxyHandler = {
    new ResourceProxyHandler(statsReceiver.scope("resourceProxyHandler"))
  }

}
