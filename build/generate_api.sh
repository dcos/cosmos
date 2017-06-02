#!/usr/bin/env bash

# Used to generate the API HTML

set -e -o pipefail

REPO_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
TARGET_DIR="${1:-"$REPO_DIR/target/api"}"
RAML_DIR="${2:-"$REPO_DIR/docs"}"

mkdir -p "$TARGET_DIR"

# Build a docker image with raml2html
sudo docker build --tag ramltools "$REPO_DIR/docker/ramltools"

for RAML in $RAML_DIR/*.raml
do
  REPO_DIR_DOCKER="/src"
  RAML_DOCKER="$REPO_DIR_DOCKER/${RAML##"$REPO_DIR/"}"

  BASENAME="$(basename "$RAML")"
  NAME="${BASENAME%.*}"
  OUT_RAML="$TARGET_DIR/$NAME.raml"
  OUT_HTML="$TARGET_DIR/$NAME.html"
  OUT_SWAGGER="$TARGET_DIR/$NAME.swagger"

  # Generate the API HTML using the built docker image
  echo "Generating $OUT_HTML"
  sudo docker run \
    --volume="$REPO_DIR":"$REPO_DIR_DOCKER":ro \
    --rm ramltools /usr/local/bin/raml2html \
    "$RAML_DOCKER" > "$OUT_HTML"

  # Generate the API Swagger using the built docker image
  echo "Generating $OUT_SWAGGER"
  sudo docker run \
    --volume="$REPO_DIR":"$REPO_DIR_DOCKER":ro \
    --rm ramltools /usr/local/bin/raml2swagger \
    "$RAML_DOCKER" > "$OUT_SWAGGER"

done

# Copy the API RAML
echo "Copying $RAML_DIR to $TARGET_DIR"
cp -R $RAML_DIR/* $TARGET_DIR
