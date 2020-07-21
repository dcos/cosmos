#!/bin/bash

set -e -o pipefail

# http://downloads.dcos.io/dcos-launch/bin/linux/dcos-launch
curl -OL %DCOS_LAUNCH_DOWNLOAD_URL%
chmod +x dcos-launch

export CLUSTER_NAME = 'cosmosit'

cat <<EOF > config.yaml
launch_config_version: 1
installer_url: https://downloads.mesosphere.com/dcos-enterprise/stable/2.0.5/dcos_generate_config.ee.sh
deployment_name: %CLUSTER_NAME%
provider: onprem
platform: aws
aws_region: us-west-2
num_masters: 1
num_private_agents: 1
num_public_agents: 1
ssh_user: core
os_name: coreos
instance_type: m4.xlarge
key_helper: true
install_prereqs: True
prereqs_script_filename: run_coreos_prereqs.sh
dcos_config:
  cluster_name: My Awesome DC/OS
  rexray_config_preset: aws
  resolvers:
    - 8.8.4.4
    - 8.8.8.8
  dns_search: mesos
  security: permissive
  master_discovery: static
  exhibitor_storage_backend: static
  customer_key: 123456-78901-234567-89012345-6789012
  license_key_contents: %DCOS_LICENSE%
EOF

./dcos-launch create
./dcos-launch wait
MASTER_IP=$(./dcos-launch describe | jq -r '.masters[0].public_ip')

echo "##teamcity[setParameter name='env.DCOS_ADMIN_ROUTER_HOST' value='$MASTER_IP']"

exit