package com.mesosphere.cosmos

import com.twitter.util.Try

/** A repository of packages that can be installed on DCOS. */
trait PackageCache {

  /** Retrieves the Marathon JSON configuration file for the given package name.
    *
    * @param packageName the package to get the configuration for
    * @return The contents of the configuration file, if present.
    */
  def get(packageName: String): Try[Option[String]]

}

object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends PackageCache {
    def get(packageName: String): Try[Option[String]] = Try(None)
  }

}
