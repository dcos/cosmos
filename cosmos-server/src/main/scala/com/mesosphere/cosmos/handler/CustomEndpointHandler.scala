package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.twitter.util.Future

trait CustomEndpointHandler[A, B] extends EndpointHandler[A, B] {
  def tryCustomPackageManager(
    a : A
  )(
    implicit session: RequestSession
  ) : Future[Option[B]]

  def orElseGet(b : Future[Option[B]])(alternative: => Future[B]): Future[B] = {
    b.flatMap {
      case None => alternative
      case Some(x) => Future(x)
    }
  }
}
