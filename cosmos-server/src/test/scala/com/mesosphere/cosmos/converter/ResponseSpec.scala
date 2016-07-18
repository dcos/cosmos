package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.rpc
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.{ServiceMarathonTemplateNotFound}
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.Cli
import com.twitter.util.{Try,Return,Throw}
import org.scalatest.FreeSpec
import com.mesosphere.cosmos.converter.Response._

final class ResponseSpec extends FreeSpec {

  val vstring = "9.87.654.3210"
  val name = "InstallResponse"
  val ver = universe.v3.model.PackageDefinition.Version(vstring)
  val notes = List(None, Some("post install notes"))
  val clis = List(None, Some(Cli(None)))
  val appid = AppId("foobar")

  "Conversion[rpc.v2.model.InstallResponse,Try[rpc.v1.model.InstallResponse]]" - {
    "success" in {
      val v2s:List[rpc.v2.model.InstallResponse] = for {
        note <- notes
        cli <- clis
      } yield (rpc.v2.model.InstallResponse(name, ver, Some(appid), note, cli))

      val v1 = rpc.v1.model.InstallResponse(name, ver.as[universe.v2.model.PackageDetailsVersion], appid)

      v2s.foreach { x => assert(x.as[Try[rpc.v1.model.InstallResponse]] == Return(v1)) }
    }

    "failure" in {
      val v2s = for {
        note <- notes
        cli <- clis
      } yield (rpc.v2.model.InstallResponse(name, ver, None, note, cli))
      v2s.foreach{ x => assert(x.as[Try[rpc.v1.model.InstallResponse]] == Throw(ServiceMarathonTemplateNotFound(name, ver))) }
    }
  }
}
