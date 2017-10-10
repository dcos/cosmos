package com.mesosphere.cosmos.test

import com.mesosphere.http.OriginHostScheme
import com.mesosphere.cosmos.http.RequestSession

object TestUtil {

  implicit val Anonymous = RequestSession(
    None,
    OriginHostScheme("localhost", OriginHostScheme.Scheme.http)
  )
}
