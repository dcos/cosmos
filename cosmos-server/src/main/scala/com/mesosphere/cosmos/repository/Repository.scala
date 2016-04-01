package com.mesosphere.cosmos.repository

import com.mesosphere.universe._
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait Repository extends PackageCollection {

  def uri: Uri

  def getPackageByReleaseVersion(
    packageName: String,
    releaseVersion: ReleaseVersion
  ): Future[PackageFiles]
}
