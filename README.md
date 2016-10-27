# Cosmos

An [orderly, harmonious, complete](http://www.thefreedictionary.com/cosmos) API for DC/OS services.

## Running tests

### Unit Tests
There is a suite of unit tests that can be ran by running `sbt clean test`

#### Scoverage

To generate an [scoverage](https://github.com/scoverage/scalac-scoverage-plugin) report for unit tests
run the following command:

```bash
sbt clean coverage test coverageReport coverageAggregate
```

The generated report can then be found at `target/scala-2.11/scoverage-report/index.html`

_NOTE_: You should never run coverage at the same time as one-jar because the produced one-jar will 
contains scoverage instrumented class files and will fail to run.

### Integration Tests
There is a suite of integration tests that can be ran by running `sbt clean it:test`

#### Scoverage

At this time it is not possible to easily generate an scoverage report for the integration suite
in `cosmos-server`. This is due to some classpath scoping issues related to the cosmos server
being forked before the integration suite is ran.

#### Requirements

- A running DC/OS cluster

#### Running the tests

The test runner will automatically start an in process zk cluster, create a temporary directory
for repo caches, and start the Cosmos server.

The test suite will then be configured to interact with this cluster by setting the following
system property:
```
-Dcom.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri
```

Any system properties that are passed to sbt will be inherited by the test suite, but not the
Cosmos server.

## Running Cosmos

Cosmos requires a ZooKeeper instance to be available. It looks for one at
`zk://localhost:2181/cosmos` by default; to override with an alternate `<zk-uri>`, specify the flag
`-com.mesosphere.cosmos.zookeeperUri=<zk-uri>` on the command line when starting Cosmos (see below).

We also need a One-JAR to run Cosmos:

```bash
sbt one-jar
```

The jar will be created in the `cosmos-server/target/scala-2.11/` directory. This can be executed
with:

```bash
mkdir /tmp/cosmos
java -jar cosmos-server/target/scala-2.11/cosmos-server_2.11-<version>-SNAPSHOT-one-jar.jar \
     -com.mesosphere.cosmos.dcosUri=<dcos-host-url>
```

It can also be exectued with ZooKeeper authentication with:

```bash
mkdir /tmp/cosmos
export ZOOKEEPER_USER <user>
export ZOOKEEPER_SECRET <secret>
java -jar cosmos-server/target/scala-2.11/cosmos-server_2.11-<version>-SNAPSHOT-one-jar.jar \
     -com.mesosphere.cosmos.dcosUri=<dcos-host-url>
```

## Versions & Compatibility

### DC/OS

The following table outlines which version of Cosmos is bundled with each version of DC/OS

| DC/OS Release Version | Cosmos Version |
|-----------------------|----------------|
| 1.6.1                 | 0.1.2          |
| 1.7.x                 | 0.1.5          |
| 1.8.x                 | 0.2.0          |

### Universe

#### Repository Format

The below table is a compatibility matrix between Cosmos and Universe repository consumption format.

*Rows represent Cosmos versions, columns represent repository formats.*

|       | application/zip (version-2.x) | application/vnd.dcos.universe.repo+json;charset=utf-8;version=v3 |
| ----- | ----------------------------- | ---------------------------------------------------------------- |
| 0.1.x | Supported                     | Not Supported                                                    |
| 0.2.0 | Supported                     | Supported                                                        |


#### Packaging Version

The below table is a compatibility matrix between Cosmos and Universe packaging versions.

*Rows represent Cosmos versions, columns represent packaging versions.*

|       |    2.0    |      3.0      |
| ----- | --------- | ------------- |
| 0.1.x | Supported | Not Supported |
| 0.2.0 | Supported | Supported     |

### API Method Version Compatibility

The following requests have constraints based on the version of the package from universe, here we outline the circumstances where the request should succeed.

#### `/package/describe`

```
Content-Type: application/vnd.dcos.package.describe-request+json;charset=utf-8;version=v1
Accept:       application/vnd.dcos.package.describe-response+json;charset=utf-8;version=v1
```
A v1 describe can succeed in the following scenarios:

1. The package being described was published as a Universe package with `packagingVersion` 2.0
2. The package being described was published as a Universe package with `packagingVersion` 3.0 and the package has a marathon template defined

```
Content-Type: application/vnd.dcos.package.describe-request+json;charset=utf-8;version=v1
Accept:       application/vnd.dcos.package.describe-response+json;charset=utf-8;version=v2
```
A v2 describe can succeed in the following scenarios:

1. The package being described was published as a Universe package with `packagingVersion` 2.0
2. The package being described was published as a Universe package with `packagingVersion` 3.0

#### `/package/render`

```
Content-Type: application/vnd.dcos.package.render-request+json;charset=utf-8;version=v1
Accept:       application/vnd.dcos.package.render-response+json;charset=utf-8;version=v1
```
A v1 render can succeed in the following scenarios:

1. The package being rendered was published as a Universe package with `packagingVersion` 2.0
2. The package being rendered was published as a Universe package with `packagingVersion` 3.0 and the package has a marathon template defined

#### `/package/install`

```
Content-Type: application/vnd.dcos.package.install-request+json;charset=utf-8;version=v1
Accept:       application/vnd.dcos.package.install-response+json;charset=utf-8;version=v1
```
A v1 install can succeed in the following scenarios:

1. The package being installed was published as a Universe package with `packagingVersion` 2.0
2. The package being installed was published as a Universe package with `packagingVersion` 3.0 and the package has a marathon template defined

```
Content-Type: application/vnd.dcos.package.install-request+json;charset=utf-8;version=v1
Accept:       application/vnd.dcos.package.install-response+json;charset=utf-8;version=v2
```
A v2 install can succeed in the following scenarios:

1. The package being installed was published as a Universe package with `packagingVersion` 2.0
2. The package being installed was published as a Universe package with `packagingVersion` 3.0 and the package has a marathon template defined
3. The package being installed was published as a Universe package with `packagingVersion` 3.0 and the package has a `.cli` object defined in it's resource set

## Reporting Problems

If you encounter a problem that seems to be related to a Cosmos bug, please create an issue at
[DC/OS Jira](https://dcosjira.atlassian.net/secure/Dashboard.jspa). To create an issue click on the
`Create` button at the top and add `cosmos` to the component.
