package com.mesosphere.cosmos.storage.installqueue

sealed trait AddResult
case object Created extends AddResult
case object AlreadyExists extends AddResult
