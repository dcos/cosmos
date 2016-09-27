package com.mesosphere.cosmos.storage

object PackageQueueHelpers {

  val packageQueueBase = "/package/packageQueue"
  val localPackageQueue = s"$packageQueueBase/local"
  val universePackageQueue = s"$packageQueueBase/universe"

  sealed trait NodeState
  case object NewNode extends NodeState
  case object NodeAlreadyExists extends NodeState
  val NodeStates = Seq(NewNode, NodeAlreadyExists)

  sealed trait PackageOperation
  case object Install extends PackageOperation
  case object Uninstall extends PackageOperation
  val PackageStates = Seq(Install, Uninstall)
}
