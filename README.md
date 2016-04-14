# Cosmos [![In Progress](https://badge.waffle.io/dcos/cosmos.png?label=in+progress&title=In+Progress)](https://waffle.io/dcos/cosmos)

An [orderly, harmonius, complete](http://www.thefreedictionary.com/cosmos) API for DCOS services.

## Running tests

### Unit Tests
There is a suite of unit tests that can be ran by running `sbt clean test`

### Integration Tests
There is a suite of integration tests that can be ran by running `sbt clean it:test`

#### Requirements

- A running DCOS cluster

#### Running the tests

The test runner will automatically start an in process zk cluster, create a temporary directory for repo caches, and
start the Cosmos server.

The test suite will then be configured to interact with this cluster by setting the following system property:
```
-Dcom.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri
```

Any system properties that are passed to sbt will be inherited by the test suite, but not the Cosmos server.

## Running Cosmos

To run the Cosmos process we need to first create a One-JAR:

```bash
sbt one-jar
```

The jar will be created in the `target/scala-2.11/` directory. This can be executed with:

```bash
java -jar target/scala-2.11/cosmos_2.11-<version>-SNAPSHOT-one-jar.jar  \
     -com.mesosphere.cosmos.dcosUri=<dcos-host-url>
```
