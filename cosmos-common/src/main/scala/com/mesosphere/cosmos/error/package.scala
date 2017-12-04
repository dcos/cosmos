package com.mesosphere.cosmos

package object error {
  type Result[A] = Either[CosmosError, A]

  implicit final class ResultOps[T](
    val value: Result[T]
  ) extends AnyVal {
    def getOrThrow: T = value.fold(ce => throw ce.exception, identity)
  }
}
