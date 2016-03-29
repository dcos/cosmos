package com.mesosphere.cosmos

private[cosmos] case class ConnectionDetails(host: String, port: Int, tls: Boolean = false)
