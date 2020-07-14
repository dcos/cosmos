#!/bin/bash

set -e -o pipefail

AUTH_TOKEN="token="$(curl -s --insecure -X "POST" -H "Content-Type: application/json" --data '{ "uid": "bootstrapuser", "password": "deleteme" }' "$CLUSTER_URL/acs/api/v1/auth/login" | jq -r ".token")
if [[ ! $AUTH_TOKEN || $AUTH_TOKEN == "null" ]]; then
  echo "\n===================================== ERROR ===================================="
  echo "Could not obtain Authorization Token for \$CLUSTER_URL='$CLUSTER_URL'";
  echo "================================================================================\n\n"
  exit 1;
fi

echo $AUTH_TOKEN
