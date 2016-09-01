package com.mesosphere.cosmos.storage

import java.io.InputStream

import com.netaporter.uri.Uri
import com.twitter.io.Reader
import com.twitter.util.Future

import com.mesosphere.cosmos.http.MediaType

trait ObjectStorage {
  def write(
    name: String,
    body: InputStream,
    contentLength: Long,
    contentType: Option[MediaType] = None
  ): Future[Unit]
  def read(name: String): Future[Reader]
  def list(directory: String): Future[ObjectStorage.ObjectList]
  def listNext(token: ObjectStorage.ListToken): Future[ObjectStorage.ObjectList]
  def getUrl(name: String): Option[Uri]
}

object ObjectStorage {
  trait ListToken

  trait ObjectList {
    val objects: List[String]
    val directories: List[String]
    val listToken: Option[ListToken]
  }
}
