#!/bin/bash

# Provides a convenient way to test the TeamCity build scripts locally.
# Can also be used to run the tests locally without an existing DCOS cluster.

set -e -o pipefail

# Taken from http://stackoverflow.com/a/246128
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "${SCRIPT_DIR}/.."

export CLUSTER_ID=$("${SCRIPT_DIR}/start_ccm_cluster.sh")
: "${CLUSTER_ID:?}"

export DCOS_ADMIN_ROUTER_HOST=$("${SCRIPT_DIR}/wait_for_ccm_cluster.sh")
: "${DCOS_ADMIN_ROUTER_HOST:?}"

sbt test

"${SCRIPT_DIR}/delete_ccm_cluster.sh"

exit
