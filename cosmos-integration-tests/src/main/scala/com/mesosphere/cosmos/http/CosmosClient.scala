package com.mesosphere.cosmos.http

import com.twitter.finagle.http.Response
import com.twitter.util.Future

final class CosmosClient {
  def submit(request: HttpRequest): Future[Response] = ???
  def direct: Boolean = ???
}
