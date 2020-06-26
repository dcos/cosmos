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
      steps {
        ansiColor('xterm') {
          sh 'sbt test oneJar'
        }
      }
      post {
        always {
          junit '**/test-reports/*.xml'
        }
      }
    }

    stage('Integration Test') {
      agent {
        node {
          label 'medium'
        }
      }
      steps {
        ansiColor('xterm') {
          sh 'sbt it:test'
        }
      }
      post {
        always {
          junit '**/test-reports/*.xml'
        }
      }
    }
  }
}