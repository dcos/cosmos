package com.mesosphere.cosmos.storage

sealed trait PackageAddResult
case object Created extends PackageAddResult
case object AlreadyExists extends PackageAddResult
