#!/bin/bash

# Used by the TeamCity build for the project

set -ex -o pipefail

# Check that required env vars are defined
: "${CCM_AUTH_TOKEN:?}"
: "${CCM_POLL_PERIOD:?}"
: "${CLUSTER_ID:?}"

# Wait for cluster to come up
declare -i poll_period=$CCM_POLL_PERIOD
declare -i seconds_until_timeout=$((60 * 30))
while (("$seconds_until_timeout" >= "0")); do
    STATUS=$(
        http \
            --ignore-stdin \
            "https://ccm.mesosphere.com/api/cluster/${CLUSTER_ID}/" \
            "Authorization:Token ${CCM_AUTH_TOKEN}" \
      | jq ".status"
    )

    if (("$STATUS" == "0")); then
        break
    elif (("$STATUS" == "7")); then
       # CCM says cluster creation failed
       exit 7
    fi

    sleep "$poll_period"
    let "seconds_until_timeout -= $poll_period"
done

if (("$seconds_until_timeout" <= "0")); then
    exit 2
fi

# Get Adminrouter IP address
CLUSTER_INFO=$(
    http \
        --ignore-stdin \
        GET \
        "https://ccm.mesosphere.com/api/cluster/${CLUSTER_ID}/" \
        "Authorization:Token ${CCM_AUTH_TOKEN}" \
  | jq ".cluster_info"
)
eval CLUSTER_INFO="$CLUSTER_INFO"    # Unescape JSON

DCOS_IP=$(echo "$CLUSTER_INFO" | jq ".MastersIpAddresses[0]")
DCOS_IP="${DCOS_IP:1:-1}"    # Remove JSON string quotes
echo "$DCOS_IP"

exit
