package com.mesosphere.cosmos

private object Config {

  private[cosmos] lazy val DcosHost: String = getOrFail("DCOS_ADMIN_ROUTER_HOST")
  private[cosmos] lazy val UniverseBundleUri: String = getOrFail("UNIVERSE_BUNDLE_URI")
  private[cosmos] lazy val UniverseCacheDir: String = getOrFail("UNIVERSE_CACHE_DIR")

  private[this] def getOrFail(
    envVar: String,
    fromEnv: collection.Map[String, String] = sys.env
  ): String = {
    fromEnv
      .getOrElse(envVar, throw new RuntimeException(s"Undefined environment variable: $envVar"))
  }

}
