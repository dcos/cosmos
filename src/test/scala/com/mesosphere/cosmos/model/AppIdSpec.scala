package com.mesosphere.cosmos.model

import cats.data.Xor
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.circe.Decoders._
import com.netaporter.uri.dsl._
import io.circe.parse._
import io.circe.syntax._
import io.circe.Json

final class AppIdSpec extends UnitSpec {

  private[this] val relative: String = "cassandra/dcos"
  private[this] val absolute: String = s"/$relative"

  "AppId tests" in {
    assertResult(absolute)(AppId(absolute).toString)
    assertResult(absolute)(AppId(relative).toString)

    assertResult(AppId(absolute))(AppId(relative))
    assertResult(AppId(absolute).hashCode)(AppId(relative).hashCode)

    assertResult(Json.string(absolute))(AppId(relative).asJson)
    assertResult(Xor.Right(AppId(absolute)))(decode[AppId](relative.asJson.noSpaces))

    assertResult("cassandra" / "dcos")(AppId(relative).toUri)
  }

}
