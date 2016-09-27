package com.mesosphere.cosmos.storage

sealed trait PackageRemoveResult
case object Removed extends PackageRemoveResult
case object AlreadyRemoved extends PackageRemoveResult
