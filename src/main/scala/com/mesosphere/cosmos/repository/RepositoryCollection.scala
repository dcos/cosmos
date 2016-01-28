package com.mesosphere.cosmos.repository

import com.netaporter.uri.Uri
import com.twitter.util.Future

/** Manages a sequence of package repositories.
  *
  * The concrete implementation of this would be backed by ZooKeeper.
  */
private trait RepositoryCollection {

  def list: Future[Seq[Repository]]

  def add(name: String, source: Uri, index: Int): Future[Unit]

  def deleteByName(name: String): Future[Unit]

  def deleteBySource(source: Uri): Future[Unit]

}
