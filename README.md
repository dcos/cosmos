# Cosmos [![In Progress](https://badge.waffle.io/mesosphere/cosmos.png?label=in+progress&title=In+Progress)](https://waffle.io/mesosphere/cosmos)

An [orderly, harmonius, complete](http://www.thefreedictionary.com/cosmos) API for DCOS services.

## Running tests

Note that the tests currently leave some apps running on Marathon, and cannot be run again
successfully until those apps are destroyed. This is a temporary inconvenience until we add support
for keeping test runs independent of each other.

### Requirements

- SBT
- An existing DCOS cluster or access to `ccm.mesosphere.com`

### Running with an existing cluster

If the adminrouter host for the cluster is `http://your.adminrouter.host` and the universe
repository that you would like to use is `http://your.universe.repository/url`, then we can run
the unittest with:

```bash
sbt test
```

And the integration tests with:

```bash
sbt -Dcom.mesosphere.cosmos.dcosUri=http://your.adminrouter.host -Dcom.mesosphere.cosmos.universeBundleUri=http://your.universe.repository/url
```

### Running with a temporary cluster

The `build/run_tests.sh` script will ask CCM for a temporary DCOS cluster:

```bash
$ ./build/run_tests.sh
```

## Running Cosmos

To run the Cosmos process we need to first create a One-JAR:

```bash
$ sbt one-jar
```

The jar will be created in the `target/scala-2.11/` directory. This can be executed with:

```bash
java -jar target/scala-2.11/cosmos_2.11-<version>-SNAPSHOT-one-jar.jar  \
     -com.mesosphere.cosmos.dcosUri=<dcos-host-url>
```
