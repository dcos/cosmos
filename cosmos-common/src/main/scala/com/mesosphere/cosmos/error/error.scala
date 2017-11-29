package com.mesosphere

import com.mesosphere.cosmos.error.CosmosError

package object error {
  type Result[A] = Either[CosmosError, A]
}
