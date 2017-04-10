package com.mesosphere

import com.twitter.util.Future

object TestUtil {

  def eventualFuture[T](
    future: () => Future[Option[T]]
  ): Future[T] = {
    future().flatMap {
      case Some(value) => Future.value(value)
      case None => eventualFuture(future)
    }
  }

}
