# DC/OS Package Manager (Cosmos)

Provides an API for the [orderly, harmonious, and complete](http://www.thefreedictionary.com/cosmos)
management of DC/OS service packages.


Teamcity CI : [Build & Compile](https://teamcity.mesosphere.io/viewType.html?buildTypeId=DcosIo_Cosmos_Ci&guest=1) and [Integration Tests](https://teamcity.mesosphere.io/viewType.html?buildTypeId=DcosIo_Cosmos_FullClusterIntegrationTests&guest=1)

#### Table of Contents
- [Running tests](#running-tests)
  - [Scala style checks](#scala-style-checks)
  - [Unit Tests](#unit-tests)
    - [Scoverage](#scoverage)
  - [Integration Tests](#integration-tests)
    - [Scoverage](#scoverage)
    - [Requirements](#requirements)
    - [Running the tests](#running-the-tests)
      - [Example configurations](#example-configurations)
- [Running Cosmos](#running-cosmos)
  - [Cosmos Admin Portal](#cosmos-admin-portal)
- [Project structure](#project-structure)
  - [RPC Conventions](#rpc-conventions)
- [Versions & Compatibility](#versions--compatibility)
  - [DC/OS](#dcos)
  - [Universe](#universe)
    - [Repository Format](#repository-format)
    - [Packaging Version](#packaging-version)
- [API Documentation](#api-documentation)
  - [Cosmos Version 0.3.x](#cosmos-version-03x)
- [Reporting Problems](#reporting-problems)

## Running tests

### Scala style checks
This project enforces certain scalastyle rules. To run those check against the code run:

```bash
sbt scalastyle
```

### Unit Tests
There is a suite of unit tests that can be ran by running `sbt clean test:test`

#### Scoverage

To generate an [scoverage](https://github.com/scoverage/scalac-scoverage-plugin) report for unit
tests run the following command:

```bash
sbt clean coverage test:test coverageReport coverageAggregate
```

The generated report can then be found at `target/scala-2.12/scoverage-report/index.html`

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

The integration tests support three ways of configuring the tests. This is done using the following
system properties:

1. `com.mesosphere.cosmos.dcosUri` - Location of the DC/OS cluster as an HTTP URL.
1. `com.mesosphere.cosmos.boot` - If `true` or undefined the integration tests will automatically
execute the Cosmos defined in this repository. If `false` then the integration tests will not
execute a Cosmos.
1. `com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri` - This property is not
required. If set to a URL, it will override the default value. The integration tests assume that
the Cosmos described in this system property is configured to control the same cluster described in
`com.mesosphere.cosmos.dcosUri`

##### Example configurations

1. Run the integration tests against the Cosmos implemented in this repo. This is done by
automatically starting an in process ZooKeeper cluster and a Cosmos server that controls a DC/OS
cluster. This configuration can be enabled by setting the `com.mesosphere.cosmos.dcosUri`
system property.

```bash
export COSMOS_AUTHORIZATION_HEADER="token=$(http --ignore-stdin <dcos-host-url>/acs/api/v1/auth/login uid=<dcos-user> password=<user-password> | jq -r ".token")"
sbt -Dcom.mesosphere.cosmos.dcosUri=<dcos-host-url> \
    clean it:test
```

2. Run the integration tests against the Cosmos running in a DC/OS cluster. This configuration can
be enabled by setting the `com.mesosphere.cosmos.dcosUri` and `com.mesosphere.cosmos.boot=false`
system properties.

```bash
export COSMOS_AUTHORIZATION_HEADER="token=$(http --ignore-stdin <dcos-host-url>/acs/api/v1/auth/login uid=<dcos-user> password=<user-password> | jq -r ".token")"
sbt -Dcom.mesosphere.cosmos.dcosUri=<dcos-host-url> \
    -Dcom.mesosphere.cosmos.boot=false \
    clean it:test
```

3. Run the integration tests against a Cosmos already configured to control a DC/OS cluster. This
configuration can be enabled by setting the `com.mesosphere.cosmos.dcosUri`,
`com.mesosphere.cosmos.boot=false` and
`com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri` system properties.

```bash
export COSMOS_AUTHORIZATION_HEADER="token=$(http --ignore-stdin <dcos-host-url>/acs/api/v1/auth/login uid=<dcos-user> password=<user-password> | jq -r ".token")"
sbt -Dcom.mesosphere.cosmos.dcosUri=<dcos-host-url> \
    -Dcom.mesosphere.cosmos.boot=false \
    -Dcom.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri=http://localhost:7070 \
    clean it:test
```

To run a single test, something like the following can be used in the sbt console:
```bash
# to run a single test suite
it:testOnly *ServiceUpdateSpec
# To run a single test from a single suite
it:testOnly *ServiceUpdateSpec -- -z "user should be able to update a service via custom manager"
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

The jar will be created in the `cosmos-server/target/scala-2.12/` directory. This can be executed
with:

```bash
java -jar cosmos-server/target/scala-2.12/cosmos-server_2.12-<version>-SNAPSHOT-one-jar.jar \
     -com.mesosphere.cosmos.dcosUri <dcos-host-url>
```

It can also be executed with ZooKeeper authentication with:

```bash
export ZOOKEEPER_USER <user>
export ZOOKEEPER_SECRET <secret>
java -jar cosmos-server/target/scala-2.12/cosmos-server_2.12-<version>-SNAPSHOT-one-jar.jar \
     -com.mesosphere.cosmos.dcosUri <dcos-host-url>
```

### Cosmos Admin Portal

Cosmos exposes an admin portal at `http://<cosmos-host>:9990/admin`. If Cosmos is running locally
and you are just interested in the metrics run the following command.

```bash
curl http://localhost:9990/admin/metrics.json
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

### RPC Conventions

All the list of RPCs that cosmos supports are located in `com/mesosphere/cosmos/rpc` package in the
`cosmos-common` module. The RPC's are structured according to their version like `v1`, `v2` and so on.
As `cosmos` grows, only the two most recent versions of rpc will be supported. Every time a new rpc is
added, the oldest rpc will be removed if there are more than two versions. In essence, this means
that the tail version should always be considered as deprecated.

## Versions & Compatibility

### DC/OS

The following table outlines which version of Cosmos is bundled with each version of DC/OS

| DC/OS Release Version | Cosmos Version |
|-----------------------|----------------|
| &ge; 1.6.1            | 0.1.2          |
| &ge; 1.7.0            | 0.1.5          |
| &ge; 1.8.0            | 0.2.0          |
| &ge; 1.8.9            | 0.2.2          |
| &ge; 1.9.0            | 0.3.0          |
| &ge; 1.9.1            | 0.3.1          |
| &ge; 1.10.0           | 0.4.0          |

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

### [Cosmos Version 0.3.x Docs](https://docs.mesosphere.com/1.10/deploying-services/package-api/)

### [Cosmos Version 0.4.x Docs](https://docs.mesosphere.com/1.11/deploying-services/package-api/)

The tooling for packaging docs is at [mesosphere/packaging-docs](https://github.com/mesosphere/packaging-docs)

## Reporting Problems

If you encounter a problem that seems to be related to a Cosmos bug, please create an issue at
[DC/OS Jira](https://jira.mesosphere.com/). To create an issue click on the
`Create` button at the top and add `cosmos` to the component field.
