#!/bin/bash

# Used by the TeamCity build for this project

set -e -o pipefail

# Check that required env vars are defined
: "${CCM_AUTH_TOKEN:?}"
: "${CLUSTER_ID:?}"

http \
    --ignore-stdin \
    DELETE \
    "https://ccm.mesosphere.com/api/cluster/${CLUSTER_ID}/" \
     "Authorization:Token ${CCM_AUTH_TOKEN}"

exit
