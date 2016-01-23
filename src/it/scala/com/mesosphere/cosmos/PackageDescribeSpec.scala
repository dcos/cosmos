package com.mesosphere.cosmos

import cats.data.Xor
import cats.data.Xor.Right
import com.mesosphere.cosmos.model._
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import com.twitter.finagle.{Http, Service}
import com.twitter.util._
import io.circe.parse._
import io.circe.generic.auto._
import io.circe.Json
import org.scalatest.FreeSpec

final class PackageDescribeSpec extends FreeSpec with CosmosSpec {

  import IntegrationHelpers._
  import PackageDescribeSpec._

  "The package describe endpoint" - {
    "don't install if specified version is not found" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = Await.result(UniversePackageCache(UniverseUri, universeDir))

        runService(packageCache = universeCache) { apiClient =>
          forAll (PackageDummyVersionsTable) { (packageName, packageVersion) =>
            apiClient.describeAndAssertError(
              packageName = packageName,
              status = Status.BadRequest,
              content = errorJson(
                s"Version [$packageVersion] of package [$packageName] not found"),
              version = Some(packageVersion)
            )
          }
        }
      }
    }

    "can successfully describe helloworld from Universe" in {
      val _ = withTempDirectory { universeDir =>
        val universeCache = Await.result(UniversePackageCache(UniverseUri, universeDir))

        runService(packageCache = universeCache) { apiClient =>
          apiClient.describeHelloworld()
          apiClient.describeHelloworld(Some("0.1.0"))
        }
      }
    }
  }

  private[this] def runService[A](
    dcosClient: Service[Request, Response] = Services.adminRouterClient(dcosHost()),
    packageCache: PackageCache
  )(
    f: DescribeTestAssertionDecorator => Unit
  ): Unit = {
    val service = new Cosmos(
      packageCache,
      new MarathonPackageRunner(adminRouter),
      (r : UninstallRequest) => { Future.value(UninstallResponse(Nil)) }
    ).service
    val server = Http.serve(s":$servicePort", service)
    val client = Http.newService(s"127.0.0.1:$servicePort")

    try {
      f(new DescribeTestAssertionDecorator(client))
    } finally {
      Await.all(server.close(), client.close(), service.close())
    }
  }
}

private object PackageDescribeSpec extends CosmosSpec {

  private val UniverseUri = Uri.parse("https://github.com/mesosphere/universe/archive/cli-test-3.zip")

  private val PackageDummyVersionsTable = Table(
    ("package name", "version"),
    ("helloworld", "a.b.c"),
    ("cassandra", "foobar")
  )

  val HelloworldPackageDef = PackageDefinition(
    name = "helloworld",
    version = "0.1.0",
    website = Some("https://github.com/mesosphere/dcos-helloworld"),
    maintainer = "support@mesosphere.io",
    description = "Example DCOS application package",
    preInstallNotes = Some("A sample pre-installation message"),
    postInstallNotes = Some("A sample post-installation message"),
    tags = List("mesosphere", "example", "subcommand")
  )

  val HelloworldResource = Resource()

  case class HelloworldPort(`type`: String, default: Int)
  case class HelloworldProperties(port: HelloworldPort)
  case class HelloworldConfig(`$schema`: String, `type`: String, properties: HelloworldProperties, additionalProperties: Boolean)

  val HelloworldConfigDef = HelloworldConfig(
    "http://json-schema.org/schema#",
    "object",
    HelloworldProperties(HelloworldPort("integer", 8080)),
    false
  )

  case class HelloworldCommand(pip: List[String])
  val HelloworldCommandDef = HelloworldCommand(
    List("dcos<1.0", "git+https://github.com/mesosphere/dcos-helloworld.git#dcos-helloworld=0.1.0")
  )

  case class HelloworldDocker(image: String, network: String)
  case class HelloworldContainer(`type`: String, docker: HelloworldDocker)
  case class HelloworldMustache(
    id: String,
    cpus: Double,
    mem: Int,
    instances: Int,
    cmd: String,
    container: HelloworldContainer
  )

  val HelloworldMustacheDef = HelloworldMustache(
    id = "helloworld",
    cpus = 1.0,
    mem = 512,
    instances = 1,
    cmd = "python3 -m http.server {{port}}",
    container = HelloworldContainer("DOCKER", HelloworldDocker("python:3", "HOST"))
  )
}

private final class DescribeTestAssertionDecorator(apiClient: Service[Request, Response]) extends CosmosSpec {
  import PackageDescribeSpec._

  final val DescribeEndpoint = "v1/package/describe"

  private[cosmos] def describeAndAssertError(
    packageName: String,
    status: Status,
    content: Json,
    version: Option[String] = None
  ): Unit = {
    val response = describeRequest(apiClient, packageName, version)
    assertResult(status)(response.status)
    assertResult(Xor.Right(content))(parse(response.contentString))
  }

  private[cosmos] def describeHelloworld(version: Option[String] = None) = {
    val response = describeRequest(apiClient, "helloworld", version)
    assertResult(Status.Ok)(response.status)
    val Right(packageInfo) = parse(response.contentString)

    val Right(packageJson) = packageInfo.cursor.get[PackageDefinition]("package")
    assertResult(HelloworldPackageDef)(packageJson)

    val Right(resourceJson) = packageInfo.cursor.get[Resource]("resource")
    assertResult(HelloworldResource)(resourceJson)

    val Right(configJson) = packageInfo.cursor.get[HelloworldConfig]("config")
    assertResult(HelloworldConfigDef)(configJson)

    val Right(commandJson) = packageInfo.cursor.get[HelloworldCommand]("command")
    assertResult(HelloworldCommandDef)(commandJson)
  }

  private[this] def describeRequest(
    apiClient: Service[Request, Response],
    packageName: String,
    versionOpt: Option[String]
  ): Response = {
    val versionParam = versionOpt.map { version => s"&packageVersion=$version" }.getOrElse("")
    val request = requestBuilder(s"$DescribeEndpoint?packageName=$packageName$versionParam")
      .buildGet()
    Await.result(apiClient(request))
  }
}


