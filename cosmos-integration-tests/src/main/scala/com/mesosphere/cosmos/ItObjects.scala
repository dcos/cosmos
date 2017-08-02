package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.jawn._
import _root_.io.circe.syntax._
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.universe
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.common.JsonUtil
import com.twitter.finagle.http.Fields
import java.nio.ByteBuffer
import java.util.Base64
import org.scalatest.prop.TableFor2

object ItObjects {

  val v4TestUniverse: String = {
    ItUtil.getRepoByName("V4TestUniverse")
  }

  val helloWorldMarathonMustache: String = {
    """{
      |  "id": "{{name}}",
      |  "cpus": 1.0,
      |  "mem": 512,
      |  "instances": 1,
      |  "cmd": "python3 -m http.server {{port}}",
      |  "container": {
      |    "type": "DOCKER",
      |    "docker": {
      |      "image": "python:3",
      |      "network": "HOST"
      |    }
      |  }
      |}
    """.stripMargin
  }

  // scalastyle:off line.size.limit
  val helloWorldPackageDefinition0: String = {
    """
      |{
      |    "command": {
      |        "pip": [
      |            "dcos<1.0",
      |            "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0"
      |        ]
      |    },
      |    "config": {
      |        "$schema": "http://json-schema.org/schema#",
      |        "additionalProperties": false,
      |        "properties": {
      |            "name" : {
      |                "type" : "string",
      |                "default" : "helloworld"
      |            },
      |            "port": {
      |                "default": 8080,
      |                "type": "integer"
      |            }
      |        },
      |        "additionalProperties" : false,
      |        "type": "object"
      |    },
      |    "description": "Example DCOS application package",
      |    "maintainer": "support@mesosphere.io",
      |    "marathon": {
      |        "v2AppMustacheTemplate": "ewogICJpZCI6ICJ7e25hbWV9fSIsCiAgImNwdXMiOiAxLjAsCiAgIm1lbSI6IDUxMiwKICAiaW5zdGFuY2VzIjogMSwKICAiY21kIjogInB5dGhvbjMgLW0gaHR0cC5zZXJ2ZXIge3twb3J0fX0iLAogICJjb250YWluZXIiOiB7CiAgICAidHlwZSI6ICJET0NLRVIiLAogICAgImRvY2tlciI6IHsKICAgICAgImltYWdlIjogInB5dGhvbjozIiwKICAgICAgIm5ldHdvcmsiOiAiSE9TVCIKICAgIH0KICB9Cn0K"
      |    },
      |    "name": "helloworld",
      |    "packagingVersion": "2.0",
      |    "postInstallNotes": "A sample post-installation message",
      |    "preInstallNotes": "A sample pre-installation message",
      |    "releaseVersion": 0,
      |    "tags": [
      |        "mesosphere",
      |        "example",
      |        "subcommand"
      |    ],
      |    "version": "0.1.0",
      |    "website": "https://github.com/mesosphere/dcos-helloworld"
      |}
    """.stripMargin
  }

  val helloWorldPackageDefinition3: String = {
    """
      |{
      |    "config": {
      |        "$schema": "http://json-schema.org/schema#",
      |        "additionalProperties": false,
      |        "properties": {
      |            "name" : {
      |                "type" : "string",
      |                "default" : "helloworld"
      |            },
      |            "port": {
      |                "default": 8080,
      |                "type": "integer"
      |            }
      |        },
      |        "additionalProperties" : false,
      |        "type": "object"
      |    },
      |    "description": "Example DCOS application package",
      |    "maintainer": "support@mesosphere.io",
      |    "marathon": {
      |        "v2AppMustacheTemplate": "ewogICJpZCI6ICJ7e25hbWV9fSIsCiAgImNwdXMiOiAxLjAsCiAgIm1lbSI6IDUxMiwKICAiaW5zdGFuY2VzIjogMSwKICAiY21kIjogInB5dGhvbjMgLW0gaHR0cC5zZXJ2ZXIge3twb3J0fX0iLAogICJjb250YWluZXIiOiB7CiAgICAidHlwZSI6ICJET0NLRVIiLAogICAgImRvY2tlciI6IHsKICAgICAgImltYWdlIjogInB5dGhvbjozIiwKICAgICAgIm5ldHdvcmsiOiAiSE9TVCIKICAgIH0KICB9Cn0K"
      |    },
      |    "name": "helloworld",
      |    "packagingVersion": "4.0",
      |    "postInstallNotes": "A sample post-installation message",
      |    "preInstallNotes": "A sample pre-installation message",
      |    "releaseVersion": 3,
      |    "tags": [
      |        "mesosphere",
      |        "example",
      |        "subcommand"
      |    ],
      |    "version": "0.4.0",
      |    "website": "https://github.com/mesosphere/dcos-helloworld"
      |}
      |
    """.stripMargin
  }

  val helloWorldPackageDefinition4: String = {
    """
      |{
      |    "config": {
      |        "$schema": "http://json-schema.org/schema#",
      |        "additionalProperties": false,
      |        "properties": {
      |            "name" : {
      |                "type" : "string",
      |                "default" : "helloworld"
      |            },
      |            "port": {
      |                "default": 8080,
      |                "type": "integer"
      |            }
      |        },
      |        "additionalProperties" : false,
      |        "type": "object"
      |    },
      |    "description": "Example DCOS application package",
      |    "downgradesTo": [
      |        "0.4.0"
      |    ],
      |    "maintainer": "support@mesosphere.io",
      |    "marathon": {
      |        "v2AppMustacheTemplate": "ewogICJpZCI6ICJ7e25hbWV9fSIsCiAgImNwdXMiOiAxLjAsCiAgIm1lbSI6IDUxMiwKICAiaW5zdGFuY2VzIjogMSwKICAiY21kIjogInB5dGhvbjMgLW0gaHR0cC5zZXJ2ZXIge3twb3J0fX0iLAogICJjb250YWluZXIiOiB7CiAgICAidHlwZSI6ICJET0NLRVIiLAogICAgImRvY2tlciI6IHsKICAgICAgImltYWdlIjogInB5dGhvbjozIiwKICAgICAgIm5ldHdvcmsiOiAiSE9TVCIKICAgIH0KICB9Cn0K"
      |    },
      |    "minDcosReleaseVersion": "1.10",
      |    "name": "helloworld",
      |    "packagingVersion": "4.0",
      |    "postInstallNotes": "A sample post-installation message",
      |    "preInstallNotes": "A sample pre-installation message",
      |    "releaseVersion": 4,
      |    "tags": [
      |        "mesosphere",
      |        "example",
      |        "subcommand"
      |    ],
      |    "upgradesFrom": [
      |        "0.4.0"
      |    ],
      |    "version": "0.4.1",
      |    "website": "https://github.com/mesosphere/dcos-helloworld"
      |}
      |
    """.stripMargin
  }

  val helloWorldPackageDefinition042: String = {
    """
      |{
      |    "config": {
      |        "$schema": "http://json-schema.org/schema#",
      |        "additionalProperties": false,
      |        "properties": {
      |            "name" : {
      |                "type" : "string",
      |                "default" : "helloworld"
      |            },
      |            "port": {
      |                "default": 8080,
      |                "type": "integer"
      |            }
      |        },
      |        "additionalProperties" : false,
      |        "type": "object"
      |    },
      |    "description": "Example DCOS application package",
      |    "downgradesTo": [
      |        "*"
      |    ],
      |    "maintainer": "support@mesosphere.io",
      |    "marathon": {
      |        "v2AppMustacheTemplate": "ewogICJpZCI6ICJ7e25hbWV9fSIsCiAgImNwdXMiOiAxLjAsCiAgIm1lbSI6IDUxMiwKICAiaW5zdGFuY2VzIjogMSwKICAiY21kIjogInB5dGhvbjMgLW0gaHR0cC5zZXJ2ZXIge3twb3J0fX0iLAogICJjb250YWluZXIiOiB7CiAgICAidHlwZSI6ICJET0NLRVIiLAogICAgImRvY2tlciI6IHsKICAgICAgImltYWdlIjogInB5dGhvbjozIiwKICAgICAgIm5ldHdvcmsiOiAiSE9TVCIKICAgIH0KICB9Cn0K"
      |    },
      |    "name": "helloworld",
      |    "packagingVersion": "4.0",
      |    "postInstallNotes": "A sample post-installation message",
      |    "preInstallNotes": "A sample pre-installation message",
      |    "releaseVersion": 5,
      |    "tags": [
      |        "mesosphere",
      |        "example",
      |        "subcommand"
      |    ],
      |    "upgradesFrom": [
      |        "*"
      |    ],
      |    "version": "0.4.2",
      |    "website": "https://github.com/mesosphere/dcos-helloworld"
      |}
      |
    """.stripMargin
  }
  // scalastyle:on line.size.limit

  val defaultHelloWorldPackageDefinition: (Json, Json) =
    parse(helloWorldPackageDefinition042).toOption.get -> v4TestUniverse.asJson

  val helloWorldPackageDefinitions: TableFor2[Json, Json] =
    new TableFor2(
      "Package Definition" -> "Package Source",
      parse(helloWorldPackageDefinition0).toOption.get -> v4TestUniverse.asJson,
      parse(helloWorldPackageDefinition3).toOption.get -> v4TestUniverse.asJson,
      parse(helloWorldPackageDefinition4).toOption.get -> v4TestUniverse.asJson
    )

  val metadataFields: Set[String] = {
    Set(
      "packagingVersion",
      "name",
      "version",
      "maintainer",
      "description",
      "tags",
      "selected",
      "scm",
      "website",
      "framework",
      "preInstallNotes",
      "postInstallNotes",
      "postUninstallNotes",
      "licenses",
      "images"
    )
  }

  val detailsFields: Set[String] = {
    metadataFields - "images"
  }

  def helloWorldPackageMetadata(
    packageDefinition: Json
  ): Json = {
    packageDefinition
      .asObject
      .get
      .filterKeys(metadataFields)
      .add("selected", false.asJson)
      .add("framework", false.asJson)
      .asJson
  }

  def helloWorldPackageDetails(
    packageDefinition: Json
  ): Json = {
    packageDefinition
      .asObject
      .get
      .filterKeys(detailsFields)
      .add("selected", false.asJson)
      .add("framework", false.asJson)
      .asJson
  }

  def helloWorldPackageDefinitionEnvelope(
    packageDefinition: Json
  ): Json = {
    Json.obj(
      "metadata" -> Json.obj(
        Fields.ContentType -> getContentType(packageDefinition),
        Fields.ContentEncoding -> StorageEnvelope.GzipEncoding.asJson
      ),
      "data" -> packageDefinition
    )
  }

  def renderHelloWorldMarathonMustacheNoLabels(options: Json): Json = {
    val defaultPort = 8080
    val port = options
      .hcursor
      .downField("port")
      .as[Int]
      .toOption
      .getOrElse(defaultPort)

    val defalutName = "helloworld"
    val name = options
      .hcursor
      .downField("name")
      .as[String]
      .toOption
      .getOrElse(defalutName)
    val Right(rendered) = parse(
      helloWorldMarathonMustache.replaceAllLiterally(
        "{{port}}", port.toString
      ).replaceAllLiterally(
        "{{name}}", name
      )
    )
    rendered
  }

  def decodedHelloWorldLabels(
    packageDefinition: Json,
    options: Json,
    packageSource: Json = v4TestUniverse.asJson
  ): Map[String, Json] = {
    val pkg = packageDefinition.asObject.get
    Map(
      "DCOS_PACKAGE_SOURCE" -> packageSource,
      "DCOS_PACKAGE_METADATA" -> helloWorldPackageMetadata(packageDefinition),
      "DCOS_PACKAGE_DEFINITION" -> helloWorldPackageDefinitionEnvelope(packageDefinition),
      "DCOS_PACKAGE_OPTIONS" -> options,
      "DCOS_PACKAGE_VERSION" -> pkg("version").asJson,
      "DCOS_PACKAGE_NAME" -> pkg("name").asJson
    )
  }

  def renderHelloWorldMarathonMustacheDecodedLabels(
    packageDefinition: Json,
    options: Json,
    packageSource: Json = v4TestUniverse.asJson
  ): Json = {
    val mustache = renderHelloWorldMarathonMustacheNoLabels(options)
    val labels = decodedHelloWorldLabels(packageDefinition, options, packageSource)
    mustache.asObject.get.add("labels", labels.asJson).asJson
  }

  def decodeEncodedPartsOfRenderResponse(renderResponse: Json): Json = {
    decodePackageDefinition _ andThen
      decodePackageMetadata _ andThen
      decodeOptions _ apply
      renderResponse
  }

  def helloWorldRenderResponseDecodedLabels(
    packageDefinition: Json,
    options: Json,
    packageSource: Json = v4TestUniverse.asJson
  ): Json = {
    val marathonJson = renderHelloWorldMarathonMustacheDecodedLabels(
      packageDefinition,
      options,
      packageSource
    )
    Json.obj(
      "marathonJson" -> marathonJson
    )
  }

  def dropNullKeys(json: Json): Json = {
    parse(JsonUtil.dropNullKeysPrinter.pretty(json)).toOption.get
  }

  private[this] def decodePackageMetadata(renderResponse: Json): Json = {
    renderResponse
      .hcursor
      .downField("marathonJson")
      .downField("labels")
      .downField("DCOS_PACKAGE_METADATA")
      .withFocus(base64Decode)
      .top.get
  }

  private[this] def decodePackageDefinition(renderResponse: Json): Json = {
    renderResponse
      .hcursor
      .downField("marathonJson")
      .downField("labels")
      .downField("DCOS_PACKAGE_DEFINITION")
      .withFocus(base64Decode)
      .withFocus { json =>
        val envelope = json.as[StorageEnvelope].right.get
        Json.obj(
          "metadata" -> envelope.metadata.asJson,
          "data" -> envelope.decodeData[universe.v4.model.PackageDefinition].asJson
        )
      }.top
      .get
  }

  private[this] def decodeOptions(renderResponse: Json): Json = {
    renderResponse
      .hcursor
      .downField("marathonJson")
      .downField("labels")
      .downField("DCOS_PACKAGE_OPTIONS")
      .withFocus(base64Decode)
      .top.get
  }

  def base64Decode(json: Json): Json = {
    json.asString.flatMap { str =>
      parseByteBuffer(
        ByteBuffer.wrap(Base64.getDecoder.decode(str))
      ).toOption
    }.get
  }

  private[this] def getContentType(packageDefinition: Json): Json = {
    val packagingVersion = packageDefinition.asObject.flatMap(
      _.apply("packagingVersion")
    ).get.asString.get

    packagingVersion match {
      case "2.0" => MediaTypes.universeV2Package.show.asJson
      case "3.0" => MediaTypes.universeV3Package.show.asJson
      case "4.0" => MediaTypes.universeV4Package.show.asJson
    }
  }

  val List(helloWorldPackage0, helloWorldPackage3, helloWorldPackage4): List[universe.v4.model.PackageDefinition] = {
    List(
      helloWorldPackageDefinition0,
      helloWorldPackageDefinition3,
      helloWorldPackageDefinition4
    ).map(stringToPackageDefinition)
  }

  def stringToPackageDefinition(
    packageDefinition: String
  ): universe.v4.model.PackageDefinition = {
    parse(packageDefinition).toOption.flatMap { json =>
      json.hcursor.as[universe.v4.model.PackageDefinition].toOption
    }.get
  }

}
