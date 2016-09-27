package com.mesosphere.cosmos.storage

sealed trait Operation
case object Install extends Operation
case object Uninstall extends Operation
