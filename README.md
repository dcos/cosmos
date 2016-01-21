# Cosmos [![In Progress](https://badge.waffle.io/mesosphere/cosmos.png?label=in+progress&title=In+Progress)](https://waffle.io/mesosphere/cosmos)

An [orderly, harmonius, complete](http://www.thefreedictionary.com/cosmos) API for DCOS services.

## Requirements

- SBT
- Install raml2html - This is needed to generate the HTML documentation from a RAML file.
  - Install NPM
  - Install raml2html - `npm i -g raml2html`

## Running tests

Note that the tests currently leave some apps running on Marathon, and cannot be run again
successfully until those apps are destroyed. This is a temporary inconvenience until we add support
for keeping test runs independent of each other.

### Requirements

- An existing DCOS cluster or access to `ccm.mesosphere.com`

### Running with an existing cluster

If the adminrouter host for the cluster is `your.adminrouter.host`, then:

```bash
$ DCOS_ADMIN_ROUTER_HOST=your.adminrouter.host sbt test
```

### Running with a temporary cluster

The `build/run_tests.sh` script will ask CCM for a temporary DCOS cluster:

```bash
$ ./build/run_tests.sh
```
