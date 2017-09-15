package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.OriginHostScheme

object TestUtil {

  implicit val Anonymous = RequestSession(None, OriginHostScheme("localhost", "http"))

}
