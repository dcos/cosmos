#!/bin/bash
set -o errexit -o nounset -o pipefail

DCOS_IMAGE_DIR=$(dirname $0)
PROJECT_DIR="${DCOS_IMAGE_DIR}/.."
TARGET_DIR="${DCOS_IMAGE_DIR}/target"

VERSION=${VERSION:-"dev"}
CLEAN_VERSION=${VERSION//\//_}
ONE_JAR="cosmos-${CLEAN_VERSION}-one-jar.jar"
SHA1_FILE="${ONE_JAR}.sha1"

DEPLOY_BUCKET=${DEPLOY_BUCKET:-"downloads.mesosphere.io/dcos/cosmos"}/${CLEAN_VERSION}
S3_DEPLOY_BUCKET="s3://${DEPLOY_BUCKET}"
HTTPS_DEPLOY_BUCKET="https://${DEPLOY_BUCKET}"

function clean {(

  rm -rf ${TARGET_DIR}

)}

function copy {(

  mkdir -p ${TARGET_DIR}
  cp ${PROJECT_DIR}/target/scala-2.11/cosmos_2.11-*-one-jar.jar ${TARGET_DIR}/${ONE_JAR}
  cp ${DCOS_IMAGE_DIR}/build ${TARGET_DIR}/build

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
    jq ".single_source.url |= \"${HTTPS_DEPLOY_BUCKET}/${ONE_JAR}\" | .single_source.sha1 |= \"$(getSha1)\"" \
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

