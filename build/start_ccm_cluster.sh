#!/bin/bash

# Used by the TeamCity build for the project

set -e -o pipefail

# Check that required env vars are defined
: "${CLUSTER_NAME:?}"
: "${CCM_AUTH_TOKEN:?}"

# Create cluster
CLUSTER_ID=$(
    http \
        --ignore-stdin \
        https://ccm.mesosphere.com/api/cluster/ \
        "Authorization:Token ${CCM_AUTH_TOKEN}" \
        "name=${CLUSTER_NAME}" \
        cloud_provider=0 \
        region=us-west-2 \
        time=60 \
        channel=testing/master \
        "cluster_desc=Cosmos testing cluster" \
        template=ee.single-master.cloudformation.json \
        adminlocation=0.0.0.0/0 \
        public_agents=0 \
        private_agents=1 \
  | jq ".id"
)

echo "${CLUSTER_ID}"

exit
