package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.PackageFiles
import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait PackageCache {

  def getPackageFiles(
    packageName: String,
    version: Option[String]
  ): Future[PackageFiles]

}

object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends PackageCache {

    def getPackageFiles(
      packageName: String,
      version: Option[String]
    ): Future[PackageFiles] = {
      Future.exception(PackageNotFound(packageName))
    }
  }

}
