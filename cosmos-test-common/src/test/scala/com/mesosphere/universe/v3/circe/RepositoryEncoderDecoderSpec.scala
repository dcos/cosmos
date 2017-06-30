package com.mesosphere.universe.v3.circe

import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import io.circe.Json
import io.circe.jawn.parse
import io.circe.syntax._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.scalatest.FreeSpec
import scala.io.Source

class RepositoryEncoderDecoderSpec extends FreeSpec {

  "Repository" - {
    "encode" in {
      val repo = universe.v4.model.Repository(
        List(
          universe.v3.model.V2Package(
            universe.v3.model.V2PackagingVersion,
            "cool-package",
            universe.v3.model.Version("1.2.3"),
            universe.v3.model.ReleaseVersion(1),
            "bill@cool.co",
            "some awesome package",
            universe.v3.model.Marathon(
              ByteBuffer.wrap("testing".getBytes(StandardCharsets.UTF_8))
            ),
            List("abc", "def").map(universe.v3.model.Tag(_)),
            selected = Some(false)
          ),
          universe.v3.model.V3Package(
            universe.v3.model.V3PackagingVersion,
            "cool-package",
            universe.v3.model.Version("3.2.1"),
            universe.v3.model.ReleaseVersion(2),
            "bill@cool.co",
            "some awesome package",
            List("abc", "def").map(universe.v3.model.Tag(_)),
            selected = Some(false),
            marathon = Some(
              universe.v3.model.Marathon(
                ByteBuffer.wrap("testing".getBytes(StandardCharsets.UTF_8))
              )
            ),
            minDcosReleaseVersion = Some(
              universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.8")
            )
          )
        )
      )

      val json = repo.asJson
      val inputStream = this.getClass.getResourceAsStream(
        "/com/mesosphere/universe/v3/circe/expected-encoded-repo.json"
      )
      val jsonString = Source.fromInputStream(inputStream, "UTF-8").mkString
      val Right(expected) = parse(jsonString)

      assertResult(expected)(json)
    }
    "decode" in {
      val inputStream = this.getClass.getResourceAsStream(
        "/com/mesosphere/universe/v3/circe/test-v3-repo-up-to-1.8.json"
      )
      val jsonString = Source.fromInputStream(inputStream, "UTF-8").mkString
      val repo = universe.v4.model.Repository.decodeRepository.decodeJson(
        parse(jsonString).right.get
      ).right.get

      val expected = 9
      assertResult(expected)(repo.packages.size)

      val cassandraVersions = repo.packages
        .filter(_.name == "cassandra")
        .sortBy(_.releaseVersion.value)
        .map(_.version)

      val expectedCassandraVersions = List(
        universe.v3.model.Version("0.2.0-1"),
        universe.v3.model.Version("0.2.0-2"),
        universe.v3.model.Version("2.2.5-0.2.0"),
        universe.v3.model.Version("1.0.2-2.2.5"),
        universe.v3.model.Version("1.0.4-2.2.5"),
        universe.v3.model.Version("1.0.5-2.2.5"),
        universe.v3.model.Version("1.0.5-2.2.5")
      )
      assertResult(expectedCassandraVersions)(cassandraVersions)
    }

    "decode error when package has unsupported packagingVersion" in {
      val json = Json.obj(
        "packages" -> Json.arr(
          Json.obj(
            "packagingVersion" -> "3.1".asJson,
            "name" -> "bad-package".asJson,
            "version" -> "1.2.3".asJson,
            "releaseVersion" -> 0.asJson,
            "maintainer" -> "dave@bad.co".asJson,
            "description" -> "a bad package".asJson,
            "tags" -> List("abc").asJson
          )
        )
      )

      val expectedErrorMessage = s"Expected one of ${TestingPackages.versionStringList}" +
        " for packaging version, but found " +
        "[3.1]: El(DownField(packagingVersion),true,false),El(DownArray,true,false)," +
        "El(DownField(packages),true,false)"

      val Left(decodingFailure) = universe.v4.model.Repository.decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }

    "decode error when package has unsupported tag format" in {
      val json = Json.obj(
        "packages" -> Json.arr(
          Json.obj(
            "packagingVersion" -> "3.0".asJson,
            "name" -> "bad-package".asJson,
            "version" -> "1.2.3".asJson,
            "releaseVersion" -> 0.asJson,
            "maintainer" -> "dave@bad.co".asJson,
            "description" -> "a bad package".asJson,
            "tags" -> List("bad tag").asJson
          )
        )
      )

      val expectedErrorMessage = "Value 'bad tag' does not conform to expected format ^[^\\s]+$: " +
        "El(DownArray,true,false),El(DownField(tags),true,false),El(DownArray,true,false)," +
        "El(DownField(packages),true,false)"

      val Left(decodingFailure) = universe.v4.model.Repository.decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }

    "decode error when package has unsupported releaseVersion value" in {
      val releaseVersion = -1
      val json = Json.obj(
        "packages" -> Json.arr(
          Json.obj(
            "packagingVersion" -> "3.0".asJson,
            "name" -> "bad-package".asJson,
            "version" -> "1.2.3".asJson,
            "releaseVersion" -> releaseVersion.asJson,
            "maintainer" -> "dave@bad.co".asJson,
            "description" -> "a bad package".asJson,
            "tags" -> List("tag").asJson
          )
        )
      )

      val expectedErrorMessage = "Expected integer value >= 0 for release version, but found " +
        "[-1]: El(DownField(releaseVersion),true,false),El(DownArray,true,false)," +
        "El(DownField(packages),true,false)"

      val Left(decodingFailure) = universe.v4.model.Repository.decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }
  }

}
