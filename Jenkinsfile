pipeline {
  agent none
  parameters {
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'clean'
    )
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'linux_64'
    )
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'win_64'
    )
    booleanParam (
      defaultValue: false,
      description: '',
      name: 'win_32'
    )
    booleanParam (
      defaultValue: false,
      description: '',
      name: 'win_64_xp'
    )
    booleanParam (
      defaultValue: false,
      description: '',
      name: 'win_32_xp'
    )
  }
  triggers {
    cron('H 20 * * *')
  }
  stages {
    stage('Prepare') {
      steps {
        script {
          def branchName = env.BRANCH_NAME
          def productVersion = "1.3.99"
          def pV = branchName =~ /^(release|hotfix)\\/v[\d]+.(.*)$/
          if(pV.find()) {
            productVersion = "1." + pV.group(2)
          }
          env.PRODUCT_VERSION = productVersion
        }
        script {
          env.PUBLISHER_NAME = "AO \"NOVYE KOMMUNIKACIONNYE TEHNOLOGII\""
          env.COMPANY_NAME = "R7-Office"
        }
      }
    }
    stage('Build') {
      parallel {
        stage('Linux 64-bit build') {
          agent { label 'linux_64' }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.linux_64 ) {
                utils.linuxBuild(env.BRANCH_NAME, "linux_64", params.clean)
              }
            }
          }
        }
        stage('Windows 64-bit build') {
          agent {
            node {
              label 'win_64'
              customWorkspace "C:\\r7\\${env.BRANCH_NAME}\\win_64"
            }
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_64 ) {
                utils.windowsBuild(env.BRANCH_NAME, "win_64", params.clean)
              }
            }
          }
        }
        stage('Windows 32-bit build') {
          agent {
            node {
              label 'win_32'
              customWorkspace "C:\\r7\\${env.BRANCH_NAME}\\win_32"
            }
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_32 ) {
                utils.windowsBuild(env.BRANCH_NAME, "win_32", params.clean)
              }
            }
          }
        }
        stage('Windows XP 64-bit build') {
          agent {
            node {
              label 'win_64_xp'
              customWorkspace "C:\\x8\\${env.BRANCH_NAME}"
            }
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_64_xp ) {
                utils.windowsBuild(env.BRANCH_NAME, "win_64_xp", params.clean)
              }
            }
          }
        }
        stage('Windows XP 32-bit build') {
          agent {
            node {
              label 'win_32_xp'
              customWorkspace "C:\\x4\\${env.BRANCH_NAME}"
            }
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_32_xp ) {
                utils.windowsBuild(env.BRANCH_NAME, "win_32_xp", params.clean)
              }
            }
          }
        }
      }
    }
  }
}
