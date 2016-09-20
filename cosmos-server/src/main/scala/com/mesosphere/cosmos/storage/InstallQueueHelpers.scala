package com.mesosphere.cosmos.storage

object InstallQueueHelpers {

  val installQueueBase = "/package/installStatus"
  val localInstallQueue = s"$installQueueBase/local"
  val universeInstallQueue = s"$installQueueBase/universe"

}
