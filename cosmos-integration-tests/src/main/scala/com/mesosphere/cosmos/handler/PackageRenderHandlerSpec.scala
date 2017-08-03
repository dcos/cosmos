package com.mesosphere.cosmos.handler

import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import _root_.io.circe.jawn._
import _root_.io.circe.syntax._
import com.mesosphere.cosmos.ItObjects
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.RenderRequest
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import org.scalatest.AppendedClues._
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.util.Right

class PackageRenderHandlerSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  import PackageRenderHandlerSpec._

  private[this] implicit val testContext = TestContext.fromSystemProperties()

  "PackageRenderHandler should" - {

    "succeed when attempting to render a service with a marathon template" - {
      "when no options are specified" in {
        forAll(ItObjects.helloWorldPackageDefinitions) { (packageDefinition, source) =>
          testRender(packageDefinition, source, versioned = true)
        }
      }
      "when options are specified" in {
        forAll(ItObjects.helloWorldPackageDefinitions) { (packageDefinition, source) =>
          val options = Json.obj(
            "port" -> 8888.asJson
          )
          testRender(packageDefinition, source, versioned = true, options = options)
        }
      }
    }

    "error if attempting to render a service with no marathon template" in {
      val expectedBody = ErrorResponse(
        "ServiceMarathonTemplateNotFound",
        s"Package: [enterprise-security-cli] version: [0.8.0] does not have a " +
          "Marathon template defined and can not be rendered",
        Some(JsonObject.fromMap(Map(
          "packageName" -> "enterprise-security-cli".asJson,
          "packageVersion" -> "0.8.0".asJson
        )))
      )

      val renderRequest = RenderRequest("enterprise-security-cli")

      val response = packageRender(renderRequest)

      assertResult(Status.BadRequest)(response.status)
      assertResult(MediaTypes.ErrorResponse.show)(response.contentType.get)
      val Right(actualBody) = decode[ErrorResponse](response.contentString)
      assertResult(expectedBody)(actualBody)
    }

    "render the package with the highest release version" +
      " from the lowest indexed repository when provided no version" in {
      val (packageDefinition, source) = ItObjects.defaultHelloWorldPackageDefinition
      testRender(packageDefinition, source, versioned = false)
    }

    def testRender(
      packageDefinition: Json,
      source: Json,
      versioned: Boolean,
      options: Json = Json.obj()
    ): Unit = {
      val Right(name) = packageDefinition.hcursor.downField("name").as[String]
      val Right(versionString) = packageDefinition.hcursor.downField("version").as[String]
      val version = if (versioned) Some(PackageDetailsVersion(versionString)) else None

      val renderRequest = RenderRequest(name, version, options.asObject)
      val response = packageRender(renderRequest)

      response.status shouldBe Status.Ok withClue response.contentString

      assertResult(MediaTypes.RenderResponse.show)(response.contentType.get)

      val Right(content) = parse(response.getContentString())
      val canonContent = ItObjects.dropNullKeys(
        ItObjects.decodeEncodedPartsOfRenderResponse(content)
      )
      val expectedResult = ItObjects.helloWorldRenderResponseDecodedLabels(
        packageDefinition,
        options,
        source
      )
      canonContent shouldBe expectedResult
      ()
    }

  }
}

object PackageRenderHandlerSpec {

  def packageRender(
    renderRequest: RenderRequest
  )(
    implicit testContext: TestContext
  ): Response = {
    val request = CosmosRequests.packageRender(renderRequest)
    CosmosClient.submit(request)
  }

}
