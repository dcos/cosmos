package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.{label,ServiceMarathonTemplateNotFound, internal, rpc}
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
  val ver = universe.v3.model.PackageDefinition.Version(vstring)
  val name = "ResponseSpec"
  "Conversion[rpc.v2.model.InstallResponse,Try[rpc.v1.model.InstallResponse]]" - {
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


    "success" in {
      val v1 = rpc.v1.model.InstallResponse(name, ver.as[universe.v2.model.PackageDetailsVersion], appid)

      forAll(validV2s) { x => assertResult(Return(v1))(x.as[Try[rpc.v1.model.InstallResponse]]) }
    }

    "failure" in {
      forAll(invalidV2s) { x => assertResult(Throw(ServiceMarathonTemplateNotFound(name, ver)))(x.as[Try[rpc.v1.model.InstallResponse]]) }
    }
  }
  "Conversion[internal.model.PackageDefinition,Try[rpc.v1.model.DescribeResponse]]" - {
    "success" in {
      val marathon = "dGVzdGluZw=="
      val p =  internal.model.PackageDefinition(universe.v3.model.V3PackagingVersion,
                                                name,
                                                ver.as[universe.v3.model.PackageDefinition.Version],
                                                universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
                                                "maintainer",
                                                "description",
                                                 marathon = Some(universe.v3.model.Marathon(java.nio.ByteBuffer.allocate(marathon.length).put(marathon.getBytes))))
      val d = universe.v2.model.PackageDetails(universe.v2.model.PackagingVersion("3.0"),
                                               name,
                                               ver.as[universe.v2.model.PackageDetailsVersion],
                                               "maintainer",
                                               "description",
                                               selected = Some(false),
                                               framework = Some(false))
      val r = rpc.v1.model.DescribeResponse(d,marathon)
      assertResult(Return(r))(p.as[Try[rpc.v1.model.DescribeResponse]])
    }

    "failure" in {
      val p =  internal.model.PackageDefinition(universe.v3.model.V3PackagingVersion,
                                                name,
                                                ver.as[universe.v3.model.PackageDefinition.Version],
                                                universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
                                                "maintainer",
                                                "description")
      assertResult(Throw(ServiceMarathonTemplateNotFound(name, ver)))(p.as[Try[rpc.v1.model.DescribeResponse]])
    }
  }

}
