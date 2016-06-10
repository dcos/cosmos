package com.mesosphere.cosmos.rpc.v2

import com.mesosphere.universe.v3.model.V3Package

package object model {

  // These type aliases are immutable, like everything in this package
  // They keep naming consistent across versions
  type DescribeResponse = V3Package

}
