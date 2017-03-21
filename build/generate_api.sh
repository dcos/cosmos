#!/bin/bash

# Used to generate the API HTML

set -e -o pipefail

REPO_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
TARGET_DIR="$REPO_DIR/cosmos-server/target"
mkdir -p "$TARGET_DIR"

# Build a docker image with raml2html
sudo docker build --tag ramltools "$REPO_DIR/docker/ramltools"

for SERVICE in capabilities service package
do
  # Generate the API HTML using the built docker image
  echo "Generating $SERVICE.html"
  sudo docker run --volume="$REPO_DIR":/src:ro --rm ramltools /usr/local/bin/raml2html \
    "/src/cosmos-server/src/main/resources/com/mesosphere/cosmos/handler/$SERVICE.raml" \
    > "$TARGET_DIR/$SERVICE.html"

  # Generate the API Swagger using the built docker image
  echo "Generating $SERVICE.swagger"
  sudo docker run --volume="$REPO_DIR":/src:ro --rm ramltools /usr/local/bin/raml2swagger \
    "/src/cosmos-server/src/main/resources/com/mesosphere/cosmos/handler/$SERVICE.raml" \
    > "$TARGET_DIR/$SERVICE.swagger"

  # Copy the API RAML
  echo "Copying $SERVICE.raml"
  cp "$REPO_DIR/cosmos-server/src/main/resources/com/mesosphere/cosmos/handler/$SERVICE.raml" \
    "$TARGET_DIR/$SERVICE.raml"
done
