package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.http.OriginHostScheme
import com.mesosphere.cosmos.http.RequestSession

object TestUtil {

  implicit val Anonymous = RequestSession(None, OriginHostScheme("localhost", "http"))

}
