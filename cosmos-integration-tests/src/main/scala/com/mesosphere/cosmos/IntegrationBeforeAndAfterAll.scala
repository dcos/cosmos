package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import io.circe.jawn.decode
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import scala.concurrent.duration._

trait IntegrationBeforeAndAfterAll extends BeforeAndAfterAll with Eventually { this: Suite =>

  private[this] val universeUri = "https://downloads.mesosphere.com/universe/02493e40f8564a39446d06c002f8dcc8e7f6d61f/repo-up-to-1.8.json"
  private[this] val universeConverterUri = "https://universe-converter.mesosphere.com/transform?url=" + universeUri

  val v5TestPackage = "https://infinity-artifacts.s3.amazonaws.com/cosmos-integration-test/package-with-manager/stub-universe-hello-world.json"

  // scalastyle:off method.length
  override def beforeAll(): Unit = {
    Requests.deleteRepository(Some("Universe"))

    // scalastyle:off line.size.limit
    // scalastyle:off file.size.limit
    // scalastyle:off line.contains.tab
    val customManagerMarathonAppJsonString  =
    """
      |{
      |	"labels": {
      |		"DCOS_SERVICE_NAME": "cosmos-package",
      |		"DCOS_SERVICE_PORT_INDEX": "0",
      |		"DCOS_SERVICE_SCHEME": "http"
      |	},
      |	"id": "/cosmos-package",
      |	"backoffFactor": 1.15,
      |	"backoffSeconds": 1,
      |	"cmd": "export JAVA_HOME=@(ls -d @MESOS_SANDBOX/jdk*/jre/); export JAVA_HOME=@{JAVA_HOME/}; export PATH=@(ls -d @JAVA_HOME/bin):@PATH\n\njava -classpath cosmos-server-0.6.0-SNAPSHOT-342-master-e8b383785a-one-jar.jar  com.simontuffs.onejar.Boot -admin.port=127.0.0.1:9990 -com.mesosphere.cosmos.httpInterface=0.0.0.0:7070  -com.mesosphere.cosmos.zookeeperUri=zk://leader.mesos:2181/cosmos-package",
      |	"container": {
      |		"portMappings": [{
      |			"containerPort": 7070,
      |			"hostPort": 0,
      |			"protocol": "tcp",
      |			"servicePort": 10000
      |		}],
      |		"type": "MESOS",
      |		"volumes": []
      |	},
      |	"cpus": 1,
      |	"disk": 0,
      |	"fetch": [{
      |			"uri": "https://downloads.mesosphere.com/java/server-jre-8u162-linux-x64.tar.gz",
      |			"extract": true,
      |			"executable": false,
      |			"cache": false
      |		},xo
      |		{
      |			"uri": "https://downloads.dcos.io/cosmos/0.6.0-SNAPSHOT-342-master-e8b383785a/cosmos-server-0.6.0-SNAPSHOT-342-master-e8b383785a-one-jar.jar",
      |			"extract": true,
      |			"executable": false,
      |			"cache": false
      |		}
      |	],
      |	"instances": 1,
      |	"maxLaunchDelaySeconds": 3600,
      |	"mem": 4000,
      |	"gpus": 0,
      |	"networks": [{
      |		"mode": "container/bridge"
      |	}],
      |	"requirePorts": false,
      |	"upgradeStrategy": {
      |		"maximumOverCapacity": 1,
      |		"minimumHealthCapacity": 1
      |	},
      |	"killSelection": "YOUNGEST_FIRST",
      |	"unreachableStrategy": {
      |		"inactiveAfterSeconds": 0,
      |		"expungeAfterSeconds": 0
      |	},
      |	"healthChecks": [],
      |	"constraints": []
      |}
    """
      .stripMargin
      .replaceAllLiterally("@", "$")
    // scalastyle:on line.size.limit
    // scalastyle:on method.length
    // scalastyle:on file.size.limit
    // scalastyle:on line.contains.tab

    Requests.postMarathonApp(parse(customManagerMarathonAppJsonString).toOption.get.asObject.get)
    Requests.waitForDeployments()

    //scalastyle:off magic.number
    // TODO: add a healthcheck to test marathon app and remove this
    Thread.sleep(10000) //required that custom cosmos server is started before proceeding
    //scalastyle:on magic.number

    Requests.addRepository(
      "Universe",
      universeConverterUri,
      Some(0)
    )

    //verifies v5 package can be added
    Requests.addRepository(
      "V5TestPackage",
      v5TestPackage,
      Some(0)
    )

    Requests.addRepository(
      "V4TestUniverse",
      ItObjects.V4TestUniverseConverterURI,
      Some(0)
    )

    // This package is present only in V4TestUniverse and this method ensures that the
    // package collection cache is cleared before starting the integration tests
    val _ = waitUntilCacheReloads()
  }
  // scalastyle:on method.length
  override def afterAll(): Unit = {
    Requests.deleteRepository(Some("V4TestUniverse"))
    Requests.deleteRepository(Some("V5TestPackage"))
    Requests.deleteMarathonApp(AppId(ItObjects.customManagerAppName))
    Requests.deleteRepository(None, Some(universeConverterUri))
    val _ = Requests.addRepository("Universe", "https://universe.mesosphere.com/repo")
  }

  private[this] def waitUntilCacheReloads(): Assertion = {
    val packageName = "helloworld-invalid"
    eventually(timeout(2.minutes), interval(10.seconds)) {
      val response = CosmosClient.submit(
        CosmosRequests.packageDescribeV3(rpc.v1.model.DescribeRequest(packageName, None))
      )
      assertResult(Status.Ok)(response.status)
      val Right(actualResponse) = decode[rpc.v3.model.DescribeResponse](response.contentString)
      assert(actualResponse.`package`.name == packageName)
    }
  }
}
