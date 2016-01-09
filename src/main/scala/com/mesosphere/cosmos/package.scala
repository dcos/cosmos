package com.mesosphere.cosmos

import com.twitter.util.Future

object `package` extends FileUploadPatch {

  implicit final class FutureOps[A](val fut: Future[A]) extends AnyVal {

    def flatMapOption[B, C](f: B => Future[C])(implicit ev: A => Option[B]): Future[Option[C]] = {
      fut.flatMap { a =>
        ev(a).map(f) match {
          case Some(fc) => fc.map(Some(_))
          case None => Future.value(None)
        }
      }
    }

    def mapOption[B, C](f: B => C)(implicit ev: A => Option[B]): Future[Option[C]] = {
      fut.map(a => ev(a).map(f))
    }
  }

}
