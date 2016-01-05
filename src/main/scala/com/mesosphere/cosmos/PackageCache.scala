package com.mesosphere.cosmos

/** A repository of packages that can be installed on DCOS. */
private trait PackageCache {

  /** Retrieves the Marathon JSON configuration file for the given package name.
    *
    * @param packageName the package to get the configuration for
    * @return The contents of the configuration file, if present.
    */
  private[cosmos] def get(packageName: String): Option[String]

}

private object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  private[cosmos] object empty extends PackageCache {
    private[cosmos] def get(packageName: String): Option[String] = None
  }

}
