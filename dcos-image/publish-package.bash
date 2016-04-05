#!/bin/bash
set -o errexit -o nounset -o pipefail

PROJECT_DIR=$(pwd -P)
DCOS_IMAGE_DIR="${PROJECT_DIR}/dcos-image"
TARGET_DIR="${DCOS_IMAGE_DIR}/target"

VERSION=${VERSION:-"dev"}
CLEAN_VERSION=${VERSION//\//_}
ONE_JAR="cosmos-server-${CLEAN_VERSION}-one-jar.jar"
SHA1_FILE="${ONE_JAR}.sha1"

S3_DEPLOY_BUCKET="s3://downloads.mesosphere.io/dcos/cosmos/${CLEAN_VERSION}"
HTTPS_READ_BUCKET="https://downloads.mesosphere.com/dcos/cosmos/${CLEAN_VERSION}"

function clean {(

  rm -rf ${TARGET_DIR}

)}

function copy {(

  mkdir -p ${TARGET_DIR}
  cp ${PROJECT_DIR}/cosmos-server/target/scala-2.11/cosmos-server_2.11-*-one-jar.jar ${TARGET_DIR}/${ONE_JAR}

)}


function preparePackage {(

  copy

)}

function package {(

  preparePackage

  cd ${TARGET_DIR}
  sha1sum ${ONE_JAR} > ${SHA1_FILE}
  teamcityEnvVariable "SHA1" "$(getSha1)"

)}

function deploy {(

  package

  local url="${S3_DEPLOY_BUCKET}/${ONE_JAR}"
  info "Uploading artifact to: ${url}"
  aws s3 cp ${TARGET_DIR}/${ONE_JAR} ${S3_DEPLOY_BUCKET}/${ONE_JAR}
  aws s3 cp ${TARGET_DIR}/${SHA1_FILE} ${S3_DEPLOY_BUCKET}/${SHA1_FILE}

  info "Generating buildinfo.json"
  cat ${DCOS_IMAGE_DIR}/buildinfo.json | \
    jq ".single_source.url |= \"${HTTPS_READ_BUCKET}/${ONE_JAR}\" | .single_source.sha1 |= \"$(getSha1)\"" \
      > ${TARGET_DIR}/buildinfo.json
  info "buildinfo.json written ${TARGET_DIR}/buildinfo.json"

)}

function info { echo "[info] $@" ;}

function teamcityEnvVariable {
  local name=$1
  local value=$2
  if [ -n "${TEAMCITY_VERSION}" ]; then
    echo "##teamcity[setParameter name='env.$name' value='$value']"
  fi
}

function getSha1 {
  cat ${TARGET_DIR}/${SHA1_FILE} | awk '{print $1}'
}
######################### Delegates to subcommands or runs main, as appropriate
if [[ ${1:-} ]] && declare -F | cut -d' ' -f3 | fgrep -qx -- "${1:-}"
then "$@"
else package
fi

