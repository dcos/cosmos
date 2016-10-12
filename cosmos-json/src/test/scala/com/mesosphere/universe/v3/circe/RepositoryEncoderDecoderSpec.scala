package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model.PackageDefinition._
import com.mesosphere.universe.v3.model._
import io.circe.Json
import io.circe.jawn.parse
import io.circe.syntax._
import org.scalatest.FreeSpec

import scala.io.Source
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class RepositoryEncoderDecoderSpec extends FreeSpec {

  "Repository" - {
    "encode" in {
      val repo = Repository(List(
        V2Package(
          V2PackagingVersion,
          "cool-package",
          Version("1.2.3"),
          ReleaseVersion(1).get,
          "bill@cool.co",
          "some awesome package",
          Marathon(ByteBuffer.wrap("testing".getBytes(StandardCharsets.UTF_8))),
          List("abc", "def").map(Tag(_).get),
          selected = Some(false)
        ),
        V3Package(
          V3PackagingVersion,
          "cool-package",
          Version("3.2.1"),
          ReleaseVersion(2).get,
          "bill@cool.co",
          "some awesome package",
          List("abc", "def").map(Tag(_).get),
          selected = Some(false),
          marathon = Some(Marathon(ByteBuffer.wrap("testing".getBytes(StandardCharsets.UTF_8)))),
          minDcosReleaseVersion = Some(DcosReleaseVersionParser.parseUnsafe("1.8"))
        )
      ))

      val json = repo.asJson
      val inputStream = this.getClass.getResourceAsStream("/com/mesosphere/universe/v3/circe/expected-encoded-repo.json")
      val jsonString = Source.fromInputStream(inputStream, "UTF-8").mkString
      val Xor.Right(expected) = parse(jsonString)

      assertResult(expected)(json)
    }
    "decode" in {
      val inputStream = this.getClass.getResourceAsStream(
        "/com/mesosphere/universe/v3/circe/test-v3-repo-up-to-1.8.json"
      )
      val jsonString = Source.fromInputStream(inputStream, "UTF-8").mkString
      val parsed = parse(jsonString)
      val Xor.Right(repo) = parsed.flatMap(decodeRepository.decodeJson)

      assertResult(9)(repo.packages.size)

      val cassandraVersions = repo.packages
        .filter {
          case v2: V2Package => v2.name == "cassandra"
          case v3: V3Package => v3.name == "cassandra"
        }
        .sortBy {
          case v2: V2Package => v2.releaseVersion.value
          case v3: V3Package => v3.releaseVersion.value
        }
        .map {
          case v2: V2Package => v2.version
          case v3: V3Package => v3.version
        }

      val expectedCassandraVersions = List(
        Version("0.2.0-1"),
        Version("0.2.0-2"),
        Version("2.2.5-0.2.0"),
        Version("1.0.2-2.2.5"),
        Version("1.0.4-2.2.5"),
        Version("1.0.5-2.2.5"),
        Version("1.0.5-2.2.5")
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

      val expectedErrorMessage = "Expected one of [2.0, 3.0] for packaging version, but found [3.1]: El(DownField(packagingVersion),true,false),El(DownArray,true,false),El(DownField(packages),true,false)"
      val Xor.Left(decodingFailure) = decodeRepository.decodeJson(json)

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

      val expectedErrorMessage = "Value 'bad tag' does not conform to expected format ^[^\\s]+$: El(DownArray,true,false),El(DownField(tags),true,false),El(DownArray,true,false),El(DownField(packages),true,false)"
      val Xor.Left(decodingFailure) = decodeRepository.decodeJson(json)

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

      val expectedErrorMessage = "Expected integer value >= 0 for release version, but found [-1]: El(DownField(releaseVersion),true,false),El(DownArray,true,false),El(DownField(packages),true,false)"
      val Xor.Left(decodingFailure) = decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }
  }

}
