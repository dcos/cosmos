package com.mesosphere.cosmos

import _root_.io.circe.JsonObject
import _root_.io.circe.Json
import _root_.io.circe.syntax._

object ItObjects {

  val helloworldMarathonMustache: String = {
    """
      |{
      |  "id": "helloworld",
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

  val helloworldConfig: Json = Json.obj(
    "$schema" -> "http://json-schema.org/schema#".asJson,
    "type" -> "object".asJson,
    "properties" -> Json.obj(
      "port" -> Json.obj(
        "type" -> "integer".asJson,
        "default" -> 8080.asJson
      )
    ),
    "additionalProperties" -> Json.False
  )

  val helloworldCommand: Json = {
    Json.obj(
      "pip" -> List(
        "dcos<1.0",
        "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0"
      ).asJson)
  }

  val helloworldPackage: Json = Json.obj(
    "command" -> helloworldCommand,
    "config" -> helloworldConfig,
    "description" -> "Example DCOS application package".asJson,
    "maintainer" -> "support@mesosphere.io".asJson,
    "marathon" -> Json.obj(
      "v2AppMustacheTemplate" -> helloworldMarathonMustache.asJson
    ),
    "name" -> "helloworld".asJson,
    "packagingVersion" -> "4.0".asJson,
    "postInstallNotes" -> "A sample post-installation message".asJson,
    "preInstallNotes" -> "A sample pre-installation message".asJson,
    "releaseVersion" -> 3.asJson,
    "tags" -> List(
      "mesosphere",
      "example",
      "subcommand"
    ).asJson,
    "version" -> "0.4.0".asJson,
    "website" -> "https->//github.com/mesosphere/dcos-helloworld".asJson
  )

  def helloworldPackageMetadata(
    packagingVersion: String,
    version: String
  ): Json = {
    Map(
      "website" -> "https://github.com/mesosphere/dcos-helloworld".asJson,
      "name" -> "helloworld".asJson,
      "postInstallNotes" -> "A sample post-installation message".asJson,
      "description" -> "Example DCOS application package".asJson,
      "packagingVersion" -> packagingVersion.asJson,
      "tags" -> List("mesosphere".asJson, "example".asJson, "subcommand".asJson).asJson,
      "selected" -> false.asJson,
      "framework" -> false.asJson,
      "maintainer" -> "support@mesosphere.io".asJson,
      "version" -> version.asJson,
      "preInstallNotes" -> "A sample pre-installation message".asJson
    ).asJson
  }

  def hellworldMarathonJsonNoEncoding(
    packagingVersion: String,
    version: String,
    packageSource: String
  ): JsonObject = {
    val name = "helloworld"
    JsonObject.fromIterable(List(
      "id" -> name.asJson,
      "cpus" -> 1.0.asJson,
      "mem" -> 512.asJson,
      "instances" -> 1.asJson,
      "cmd" -> "python3 -m http.server 8080".asJson,
      "container" -> Json.fromFields(List(
        "type" -> "DOCKER".asJson,
        "docker" -> Json.fromFields(List(
          "image" -> "python:3".asJson,
          "network" -> "HOST".asJson
        ))
      )),
      "labels" -> Json.fromFields(List(
        "DCOS_PACKAGE_SOURCE" -> packageSource.asJson,
        "DCOS_PACKAGE_METADATA" -> helloworldPackageMetadata(packagingVersion, version),
        "DCOS_PACKAGE_DEFINITION" -> ???,
        "DCOS_PACKAGE_OPTIONS" -> ???,
        "DCOS_PACKAGE_VERSION" -> version.asJson,
        "DCOS_PACKAGE_NAME" -> name.asJson
      ))
    ))
  }

}
