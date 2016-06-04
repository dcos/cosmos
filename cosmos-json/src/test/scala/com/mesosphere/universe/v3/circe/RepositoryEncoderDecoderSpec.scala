package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.universe.v3._
import com.mesosphere.universe.v3.PackageDefinition.{ReleaseVersion, Tag, Version}
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import io.circe.Json
import io.circe.parse._
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
          V2PackagingVersion.instance,
          "cool-package",
          Version("1.2.3"),
          ReleaseVersion(1),
          "bill@cool.co",
          "some awesome package",
          List(Tag("abc"), Tag("def")),
          selected = Some(false),
          marathon = Some(Marathon(ByteBuffer.wrap("testing".getBytes(StandardCharsets.UTF_8))))
        ),
        V3Package(
          V3PackagingVersion.instance,
          "cool-package",
          Version("3.2.1"),
          ReleaseVersion(2),
          "bill@cool.co",
          "some awesome package",
          List(Tag("abc"), Tag("def")),
          selected = Some(false),
          marathon = Some(Marathon(ByteBuffer.wrap("testing".getBytes(StandardCharsets.UTF_8)))),
          minDcosReleaseVersion = Some(DcosReleaseVersionParser.parseUnsafe("1.8"))
        )
      ))

      val json = repo.asJson
      val inputStream = this.getClass.getResourceAsStream("/com/mesosphere/universe/v3/circe/RepositoryEncoderDecoderSpec/expected-encoded-repo.json")
      val jsonString = Source.fromInputStream(inputStream, "UTF-8").mkString
      val expected = parse(jsonString)

      assertResult(expected)(Xor.Right(json))
    }
    "decode" in {
      val inputStream = this.getClass.getResourceAsStream("/com/mesosphere/universe/v3/circe/RepositoryEncoderDecoderSpec/test-v3-repo-up-to-1.8.json")
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
        "packages" -> Json.array(
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

      val expectedErrorMessage = "Supported packagingVersion: [2.0, 3.0] but was: '3.1': El(DownField(packagingVersion),true),El(DownArray,true),El(DownField(packages),true)"
      val Xor.Left(decodingFailure) = decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }

    "decode error when package has unsupported tag format" in {
      val json = Json.obj(
        "packages" -> Json.array(
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

      val expectedErrorMessage = "Value 'bad tag' does not conform to expected format ^[^\\s]+$: El(DownArray,true),El(DownField(tags),true),El(DownArray,true),El(DownField(packages),true)"
      val Xor.Left(decodingFailure) = decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }

    "decode error when package has unsupported releaseVersion value" in {
      val releaseVersion = -1
      val json = Json.obj(
        "packages" -> Json.array(
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

      val expectedErrorMessage = "Value -1 is not >= 0: El(DownField(releaseVersion),true),El(DownArray,true),El(DownField(packages),true)"
      val Xor.Left(decodingFailure) = decodeRepository.decodeJson(json)

      assertResult(expectedErrorMessage)(decodingFailure.getMessage())
    }
  }

}
