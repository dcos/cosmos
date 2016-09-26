package com.mesosphere.cosmos.storage

object PackageQueueHelpers {

  val newNode = true
  val nodeAlreadyExists = false

  val packageQueueBase = "/package/state"
  val localPackageQueue = s"$packageQueueBase/local"
  val universePackageQueue = s"$packageQueueBase/universe"

  sealed trait EnumVal
  case object Install extends EnumVal
  case object Uninstall extends EnumVal
  val PackageState = Seq(Install, Uninstall)

}
