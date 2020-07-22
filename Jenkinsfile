#!/usr/bin/env groovy

@Library('sec_ci_libs@v2-latest') _

def master_branches = ["master", ] as String[]

pipeline {
  agent none

  stages {
    stage("Verify author for PR") {
      // using shakedown node because it's a lightweight Alpine Docker image instead of full VM
      agent {
        label "small"
      }
      when {
        beforeAgent true
        changeRequest()
      }
      steps {
        user_is_authorized(master_branches, '8b793652-f26a-422f-a9ba-0d1e47eb9d89', '#dcos-security-ci')
      }
    }

    stage("Build") {
      agent {
        docker {
          image 'mesosphere/scala-sbt:marathon'
          label 'large'
        }
      }
      environment {
        DOT_M2_SETTINGS = credentials('DOT_M2_SETTINGS')
        NEXUS_MAVEN_USERNAME = credentials('NEXUS_MAVEN_USERNAME')
        NEXUS_MAVEN_PASSWORD = credentials('NEXUS_MAVEN_PASSWORD')
      }
      steps {
        ansiColor('xterm') {
          sh 'mkdir -p ~/.m2 && ln -fs ${DOT_M2_SETTINGS} ~/.m2/settings.xml'
          sh """cat <<EOF > .credentials
realm=D2iQ Proxy
host=nexus.mesosphere.com
user=$NEXUS_MAVE_USERNAME
password=$NEXUS_MAVEN_PASSWORD"""
          sh 'sbt -Dsbt.boot.credentials=.credentials -Dsbt.repository.config=ci/repositories test oneJar'
        }
      }
      post {
	    failure {
	      sh 'cat /root/.sbt/boot/update.log'
	    }
        always {
          junit '**/test-reports/*.xml'
        }
      }
    }

    stage('Integration Test') {
      agent {
        docker {
          image 'mesosphere/scala-sbt:marathon'
          label 'medium'
        }
      }
      environment {
        DCOS_LAUNCH_DOWNLOAD_URL = 'http://downloads.dcos.io/dcos-launch/bin/linux/dcos-launch'
        DCOS_LICENSE = credentials('ca159ad3-7323-4564-818c-46a8f03e1389')
      }
      steps {
        ansiColor('xterm') {
          sh './ci/launch_cluster.sh'
          sh 'sbt it:test'
        }
      }
      post {
        always {
          sh './ci/destroy_cluster.sh'
          junit '**/test-reports/*.xml'
        }
      }
    }
  }
}
