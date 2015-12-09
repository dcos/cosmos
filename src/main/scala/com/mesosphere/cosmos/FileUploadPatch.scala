package com.mesosphere.cosmos

import com.twitter.finagle.http.Request
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.util.Future
import io.finch._

import scala.util.Try

/** Finch returns a 500 when a non-multipart request is sent to an endpoint expecting a multipart
  * request. Patch FileUpload to handle a bad request so the user correctly receives a 400.
  */
trait FileUploadPatch {

  def failIfNone[A](rr: RequestReader[Option[A]]): RequestReader[A] = rr.embedFlatMap {
    case Some(value) => Future.value(value)
    case None => Future.exception(Error.NotPresent(rr.item))
  }

  def safeFileUpload(name: String): RequestReader[FileUpload] = {
    failIfNone(safeFileUploadOption(name))
  }

  def safeFileUploadOption(name: String): RequestReader[Option[FileUpload]] = {
    RequestReader { request: Request =>
      Try(request.multipart)
        .toOption
        .flatten
        .flatMap(_.files.get(name))
        .flatMap(_.headOption)
    }
  }
}
