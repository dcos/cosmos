package com.mesosphere.cosmos.http

sealed trait RpcPath {
  def path: String
}

final case class ServiceRpcPath(
  action: String
)(
  implicit testContext: TestContext
) extends RpcPath {
  override def path: String = {
    if (testContext.direct) s"/service/$action" else s"/cosmos/service/$action"
  }
}

final case class PackageRpcPath(
  action: String
) extends RpcPath {
  override def path: String = {
    s"/package/$action"
  }
}

final case class RawRpcPath(path: String) extends RpcPath
