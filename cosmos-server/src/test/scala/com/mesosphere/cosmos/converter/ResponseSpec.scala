package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.ServiceMarathonTemplateNotFound
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.Cli
import com.mesosphere.cosmos.converter.Response._

import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Try,Return,Throw}

import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks.forAll
import org.scalacheck.Gen

final class ResponseSpec extends FreeSpec {

  val vstring = "9.87.654.3210"
  val name = "InstallResponse"
  val ver = universe.v3.model.PackageDefinition.Version(vstring)
  val appid = AppId("foobar")
  val clis = List(None, Some("post install notes"))
  val notes = List(None, Some(Cli(None)))
  val validV2s = for {
    n <- Gen.oneOf(clis)
    c <- Gen.oneOf(notes)
  } yield (rpc.v2.model.InstallResponse(name, ver, Some(appid), n, c))
  val invalidV2s = for {
    n <- Gen.oneOf(clis)
    c <- Gen.oneOf(notes)
  } yield (rpc.v2.model.InstallResponse(name, ver, None, n, c))

  "Conversion[rpc.v2.model.InstallResponse,Try[rpc.v1.model.InstallResponse]]" - {
    "success" in {
      val v1 = rpc.v1.model.InstallResponse(name, ver.as[universe.v2.model.PackageDetailsVersion], appid)

      forAll(validV2s) { x => assert(x.as[Try[rpc.v1.model.InstallResponse]] == Return(v1)) }
    }

    "failure" in {
      forAll(invalidV2s) { x => assert(x.as[Try[rpc.v1.model.InstallResponse]] == Throw(ServiceMarathonTemplateNotFound(name, ver))) }
    }
  }
}
