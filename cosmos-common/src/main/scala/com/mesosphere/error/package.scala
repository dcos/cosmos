package com.mesosphere

import com.mesosphere.cosmos.error.CosmosError

package object error {
  type Result[A] = Either[CosmosError, A]

  implicit final class ResultOps[T](
    val value: Result[T]
  ) extends AnyVal {
    def getOrThrow: T = value.fold(ce => throw ce.exception, identity)
  }
}
