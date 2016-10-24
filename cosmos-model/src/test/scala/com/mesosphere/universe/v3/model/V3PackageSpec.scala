package com.mesosphere.universe.v3.model

import com.mesosphere.universe.v3.model.PackageDefinition._
import java.nio.ByteBuffer
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.util.Random

class V3PackageSpec extends FreeSpec with Matchers {
  val input = List(
    ("pkg1", Version("1.0-1"), ReleaseVersion(1).get), // semver ordering
    ("pkg1", Version("1.0-2"), ReleaseVersion(2).get),
    ("pkg1", Version("1.0-3"), ReleaseVersion(3).get),
    ("pkg2", Version("1.0"), ReleaseVersion(1).get), // semver ordering
    ("pkg2", Version("2.0"), ReleaseVersion(2).get),
    ("pkg3", Version("1.0"), ReleaseVersion(3).get), // package ordering
    ("pkg4", Version("1.0"), ReleaseVersion(4).get),
    ("pkg5", Version("1.0-1"), ReleaseVersion(1).get), // semver overrides release
    ("pkg5", Version("1.1-1"), ReleaseVersion(3).get),
    ("pkg5", Version("2.0-1"), ReleaseVersion(2).get),
    ("pkg6", Version("0.0.0.1"), ReleaseVersion(1).get), // non-semver ordering
    ("pkg6", Version("0.0.0.5"), ReleaseVersion(2).get),
    ("pkg6", Version("0.0.0.2"), ReleaseVersion(3).get),
    ("pkg7", Version("0.0.4.2"), ReleaseVersion(10).get), // semver over non-semver
    ("pkg7", Version("0.0.1"), ReleaseVersion(1).get)
  )

  "V3Package" - {
    "Ordering should work" in {
      val expected = input.map(v3Package(_))

      val actual = Random.shuffle(expected).sorted

      actual shouldBe expected
    }
  }

  "V2Package" - {
    "Ordering should work" in {
      val expected = input.map(v2Package(_))

      val actual = Random.shuffle(expected).sorted

      actual shouldBe expected
    }
  }

  "PackageDefinition" - {
    "Ordering should work" in {
      val expected = input.map(packageDefinition(_))

      val actual = Random.shuffle(expected).sorted

      actual shouldBe expected
    }
  }

  def v3Package(tuple: (String, Version, ReleaseVersion)): V3Package = {
    val (name, version, relVer) = tuple

    V3Package(
      V3PackagingVersion,
      name,
      version,
      relVer,
      "does@not.matter",
      "doesn't matter"
    )
  }

  def v2Package(tuple: (String, Version, ReleaseVersion)): V2Package = {
    val (name, version, relVer) = tuple

    V2Package(
      V2PackagingVersion,
      name,
      version,
      relVer,
      "does@not.matter",
      "doesn't matter",
      Marathon(ByteBuffer.allocate(0))
    )
  }

  def packageDefinition(tuple: (String, Version, ReleaseVersion)): PackageDefinition = {
    if (Random.nextBoolean) {
      v2Package(tuple)
    } else {
      v3Package(tuple)
    }
  }
}
