# DC/OS Package Manager (Cosmos)

Provides an API for the [orderly, harmonious, and complete](http://www.thefreedictionary.com/cosmos)
management of DC/OS service packages.

## Running tests

### Scala style checks
This project enforces certain scalastyle rules. To run those check against the code run:

```bash
sbt scalastyle test:scalastyle it:scalastyle
```

### Unit Tests
There is a suite of unit tests that can be ran by running `sbt clean test`

#### Scoverage

To generate an [scoverage](https://github.com/scoverage/scalac-scoverage-plugin) report for unit
tests run the following command:

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

The test runner will automatically start an in process zk cluster and start the Cosmos server.

The test suite will then be configured to interact with this cluster by setting the following
system properties:

```
-Dcom.mesosphere.cosmos.dcosUri
-Dcom.mesosphere.cosmos.packageStorageUri
-Dcom.mesosphere.cosmos.stagedPackageStorageUri
```

or running the following command:

```bash
export COSMOS_AUTHORIZATION_HEADER="token=$(http --ignore-stdin <dcos-host-url>/acs/api/v1/auth/login uid=<dcos-user> password=<user-password> | jq -r ".token")"
sbt -Dcom.mesosphere.cosmos.dcosUri=<dcos-host-url> \
    -Dcom.mesosphere.cosmos.packageStorageUri=file:///tmp/cosmos/packages \
    -Dcom.mesosphere.cosmos.stagedPackageStorageUri=file:///tmp/cosmos/staged-packages \
    clean it:test
```

## Running Cosmos

Cosmos requires a ZooKeeper instance to be available. It looks for one at
`zk://localhost:2181/cosmos` by default; to override with an alternate `<zk-uri>`, specify the flag
`-com.mesosphere.cosmos.zookeeperUri <zk-uri>` on the command line when starting Cosmos (see
below).

We also need a One-JAR to run Cosmos:

```bash
sbt oneJar
```

The jar will be created in the `cosmos-server/target/scala-2.11/` directory. This can be executed
with:

```bash
java -jar cosmos-server/target/scala-2.11/cosmos-server_2.11-<version>-SNAPSHOT-one-jar.jar \
     -com.mesosphere.cosmos.dcosUri <dcos-host-url> \
     -com.mesosphere.cosmos.packageStorageUri file://<absolute-path-to-package-dir> \
     -com.mesosphere.cosmos.stagedPackageStorageUri file://<absolute-path-to-staged-dir>
```

It can also be executed with ZooKeeper authentication with:

```bash
export ZOOKEEPER_USER <user>
export ZOOKEEPER_SECRET <secret>
java -jar cosmos-server/target/scala-2.11/cosmos-server_2.11-<version>-SNAPSHOT-one-jar.jar \
     -com.mesosphere.cosmos.dcosUri <dcos-host-url> \
     -com.mesosphere.cosmos.packageStorageUri file://<absolute-path-to-package-dir> \
     -com.mesosphere.cosmos.stagedPackageStorageUri file://<absolute-path-to-staged-dir>
```

## Project structure

The code is organized into several subprojects, each of which has a JAR published to the
Sonatype OSS repository. Here's an overview:

* `cosmos-test-common`
    * `src/main` directory: defines the code and resources used by both the unit and integration
    tests.
    * `src/test` directory: defines the unit tests and any resources they require.
* `cosmos-integration-tests`
    * `src/main` directory: defines the integration tests and any resources they require.
* The remaining subprojects define the main code for Cosmos, always within their `src/main`
directories.

## Versions & Compatibility

### DC/OS

The following table outlines which version of Cosmos is bundled with each version of DC/OS

| DC/OS Release Version | Cosmos Version |
|-----------------------|----------------|
| &ge; 1.6.1            | 0.1.2          |
| &ge; 1.7.0            | 0.1.5          |
| &ge; 1.8.0            | 0.2.0          |
| &ge; 1.8.9            | 0.2.1          |
| &ge; 1.9.0            | 0.3.0          |

### Universe

#### Repository Format

The below table is a compatibility matrix between Cosmos and Universe repository consumption
format.

*Rows represent Cosmos versions, columns represent repository formats.*

|       | Version 2 | Version 3     | Version 4     |
| ----- | ----------|-------------- | --------------|
| 0.1.x | Supported | Not Supported | Not Supported |
| 0.2.x | Supported | Supported     | Not Supported |
| 0.3.x | Supported | Supported     | Not Supported |
| 0.4.x | Supported | Supported     | Supported     |


#### Packaging Version

The below table is a compatibility matrix between Cosmos and Universe packaging versions.

*Rows represent Cosmos versions, columns represent packaging versions.*

|       |    2.0    |      3.0      |      4.0      |
| ----- | --------- | ------------- | ------------- |
| 0.1.x | Supported | Not Supported | Not Supported |
| 0.2.x | Supported | Supported     | Not Supported |
| 0.3.x | Supported | Supported     | Not Supported |
| 0.4.x | Supported | Supported     | Supported     |

## API Documentation

Cosmos implements three different services. The documentation for each service is outlined below.

### Cosmos Version 0.3.x

1. **Service Manager**
   1. [HTML](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/service.html)
   1. [RAML](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/service.raml)
   1. [Swagger](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/service.swagger)
1. **Package Manager**
   1. [HTML](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/package.html)
   1. [RAML](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/package.raml)
   1. [Swagger](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/package.swagger)
1. **Capability Manager**
   1. [HTML](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/capabilities.html)
   1. [RAML](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/capabilities.raml)
   1. [Swagger](https://downloads.dcos.io/cosmos/0.4.0-SNAPSHOT-232-master-2fdc5cf8ad/capabilities.swagger)

## Reporting Problems

If you encounter a problem that seems to be related to a Cosmos bug, please create an issue at
[DC/OS Jira](https://dcosjira.atlassian.net/secure/Dashboard.jspa). To create an issue click on the
`Create` button at the top and add `cosmos` to the component field.
