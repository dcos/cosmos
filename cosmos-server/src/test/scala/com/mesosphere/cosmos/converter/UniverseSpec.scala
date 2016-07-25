package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.{label,ServiceMarathonTemplateNotFound, internal, rpc}
import com.mesosphere.cosmos.test.TestUtil

import java.nio.charset.StandardCharsets

import com.mesosphere.universe
import com.mesosphere.universe.v3.model.Cli
import com.mesosphere.universe.common.ByteBuffers

import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Try,Return,Throw}

import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks.forAll
import org.scalacheck.Gen

import cats.data.Xor
import scala.io.Source

final class UniverseSpec extends FreeSpec {
  def expectV3(v2model: universe.v2.model.PackageDetails): universe.v2.model.PackageDetails = v2model.copy(
    packagingVersion = universe.v2.model.PackagingVersion("3.0")
  )
  "Conversion[universe.v3.model.PackageDefinition,universe.v2.model.PackageDetails]" - {
    assertResult(expectV3(TestUtil.MaximalV2ModelPackageDetails))(TestUtil.MaximalV3ModelPackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(expectV3(TestUtil.MinimalV2ModelPackageDetails))(TestUtil.MinimalV3ModelPackageDefinition.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V3Package,universe.v2.model.PackageDetails]" - {
    assertResult(expectV3(TestUtil.MinimalV2ModelPackageDetails))(TestUtil.MinimalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(expectV3(TestUtil.MaximalV2ModelPackageDetails))(TestUtil.MaximalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V2Package,universe.v2.model.PackageDetails]" - {
    assertResult(TestUtil.MaximalV2ModelPackageDetails)(TestUtil.MaximalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(TestUtil.MinimalV2ModelPackageDetails)(TestUtil.MinimalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V2Package,internal.model.PackageDefinition]" - {
  def excpectV2(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
    packagingVersion = universe.v3.model.V2PackagingVersion,
    marathon = Some(TestUtil.MinimalV3ModelV2PackageDefinition.marathon)
  )
    assertResult(excpectV2(TestUtil.MinimalPackageDefinition))(TestUtil.MinimalV3ModelV2PackageDefinition.as[internal.model.PackageDefinition])
/*
PackageDefinition(V2PackagingVersion,MAXIMAL,9.87.654.3210,com.mesosphere.universe.v3.model.PackageDefinition$ReleaseVersion@7fffffff,max@mesosphere.io,A complete package definition,List(Tag(all), Tag(the), Tag(things)),true,Some(git),Some(mesosphere.com),true,Some(pre-install message),Some(post-install message),Some(post-uninstall message),Some(List(License(ABC,http://foobar/a/b/c), License(XYZ,http://foobar/x/y/z))),Some(DcosReleaseVersion(Version(1),List(Version(9), Version(99)),None)),Some(Marathon(java.nio.HeapByteBuffer[pos=0 lim=17 cap=17])),Some(V3Resource(Some(Assets(Some(Map(foo.tar.gz -> http://mesosphere.com/foo.tar.gz, bar.jar -> https://mesosphere.com/bar.jar),Some(Container(Map(image1 -> docker/image:1, image2 -> docker/image:2))))),Some(Images(small.png,medium.png,large.png,Some(List(ooh.png, aah.png)))),Some(Cli(Some(Platforms(Some(Architectures(Binary(windows,mesosphere.com/windows.exe,List(HashInfo(letters,abcba), HashInfo(numbers,12321))))),Some(Architectures(Binary(linux,mesosphere.com/linux,List(HashInfo(letters,ijkji), HashInfo(numbers,13579))))),Some(Architectures(Binary(darwin,mesosphere.com/darwin,List(HashInfo(letters,xyzyx), HashInfo(numbers,02468))))))))))),Some(object[foo-> 42,bar -> "baz"]),Some(Command(List(flask, jinja, jsonschema)))),

PackageDefinition(V2PackagingVersion,MAXIMAL,9.87.654.3210,com.mesosphere.universe.v3.model.PackageDefinition$ReleaseVersion@7fffffff,max@mesosphere.io,A complete package definition,List(Tag(all), Tag(the), Tag(things)),true,Some(git),Some(mesosphere.com),true,Some(pre-install message),Some(post-install message),Some(post-uninstall message),Some(List(License(ABC,http://foobar/a/b/c), License(XYZ,http://foobar/x/y/z))),None,Some(Marathon(java.nio.HeapByteBuffer[pos=0 lim=17 cap=17])),Some(V3Resource(Some(Assets(Some(Map(foo.tar.gz -> http://mesosphere.com/foo.tar.gz, bar.jar -> https://mesosphere.com/bar.jar)),Some(Container(Map(image1-> docker/image:1, image2 -> docker/image:2))))),Some(Images(small.png,medium.png,large.png,Some(List(ooh.png, aah.png)))),None)),Some(object[foo -> 42,bar -> "baz"]),Some(Command(List(flask, jinja, jsonschema))))
  */

    assertResult(excpectV2(TestUtil.MaximalPackageDefinition))(TestUtil.MaximalV3ModelV2PackageDefinition.as[internal.model.PackageDefinition])
  }
}
