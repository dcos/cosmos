package com.mesosphere.cosmos

import com.twitter.util.Future

/** A repository of packages that can be installed on DCOS. */
trait PackageCache {

  /** Produces the Marathon JSON configuration file for the given package name.
    *
    * @param packageName the package to get the configuration for
    * @return If successful, one of:
    *  - `Some(config)`, where `config` is the successfully produced configuration;
    *  - `None`, if the package could not be found.
    */
  def get(packageName: String): Future[Option[String]]

}

object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends PackageCache {
    def get(packageName: String): Future[Option[String]] = Future.value(None)
  }

}
