package com.mesosphere.cosmos.thirdparty.marathon.model

import io.lemonlabs.uri.dsl._
import org.scalatest.FreeSpec

final class AppIdSpec extends FreeSpec {

  private[this] val relative: String = "cassandra/dcos"
  private[this] val absolute: String = s"/$relative"

  "AppId should" - {

    "consistently render" - {
      "absolute" in {
        assertResult(absolute)(AppId(absolute).toString)
      }
      "relative" in {
        assertResult(absolute)(AppId(relative).toString)
      }
    }

    "consistently construct" - {
      "absolute" in {
        assertResult(AppId(absolute))(AppId(absolute))
        assertResult(AppId(absolute).hashCode)(AppId(absolute).hashCode)
      }
      "relative" in {
        assertResult(AppId(absolute))(AppId(relative))
        assertResult(AppId(absolute).hashCode)(AppId(relative).hashCode)
      }
    }

    "generate uri" - {
      "absolute" in {
        assertResult("/cassandra" / "dcos")(AppId(absolute).toUri)
      }
      "relative" in {
        assertResult("/cassandra" / "dcos")(AppId(relative).toUri)
      }
    }
  }

}
