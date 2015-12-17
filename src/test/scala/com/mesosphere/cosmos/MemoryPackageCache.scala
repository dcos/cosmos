package com.mesosphere.cosmos

/** A package cache that stores all package information in memory. Useful for testing.
  *
  * @param packages the contents of the cache: Marathon JSON files indexed by package name
  */
private final case class MemoryPackageCache(packages: Map[String, String]) extends PackageCache {

  private[cosmos] def get(packageName: String): Option[String] = packages.get(packageName)

}
