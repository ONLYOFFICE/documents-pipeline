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
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'core'
    )
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'desktopeditor'
    )
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'documentbuilder'
    )
    booleanParam (
      defaultValue: false,
      description: '',
      name: 'documentserver'
    )
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'documentserver_ie'
    )
    booleanParam (
      defaultValue: false,
      description: '',
      name: 'documentserver_de'
    )
    booleanParam (
      defaultValue: true,
      description: '',
      name: 'test'
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
          def productVersion = "5.4.99"
          def pV = branchName =~ /^(release|hotfix)\\/v(.*)$/
          if(pV.find()) {
            productVersion = pV.group(2)
          }
          env.PRODUCT_VERSION = productVersion
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
              utils.checkoutRepos(env.BRANCH_NAME)
              
              if ( params.linux_64 ) {
                String platform = "linux_64"
                utils.linuxBuild(platform, params.clean, false)
                if ( params.core ) {
                  utils.linuxBuildCore()
                }
                if ( params.documentbuilder ) {
                  utils.linuxBuildBuilder(platform)
                }
                if ( params.desktopeditor ) {
                  utils.linuxBuildDesktop(platform)
                }
                if ( params.documentserver ) {
                  utils.linuxBuildServer()
                }
                if ( params.test ) {
                  utils.linuxTest()
                }
                if ( params.documentserver_ie || params.documentserver_de ) {
                  utils.linuxBuild(platform, false, true)
                  if ( params.documentserver_ie ) {
                    utils.linuxBuildServer("DocumentServer-IE")
                  }
                  if ( params.documentserver_de ) {
                    utils.linuxBuildServer("DocumentServer-DE")
                  }
                }
              }
            }
          }
        }
        stage('Windows 64-bit build') {
          agent {
            node {
              label 'win_64'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_64"
            }
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_64 ) {
                String platform = "win_64"
                utils.windowsBuild(platform, params.clean, false)
                if ( params.core ) {
                  utils.windowsBuildCore()
                }
                if ( params.documentbuilder ) {
                  utils.windowsBuildBuilder(platform)
                }
                if ( params.desktopeditor ) {
                  utils.windowsBuildDesktop(platform)
                }
                if ( params.documentserver ) {
                  utils.windowsBuildServer()
                }
                if ( params.documentserver_ie || params.documentserver_de ) {
                  utils.windowsBuild(platform, false, true)
                  if ( params.documentserver_ie ) {
                    utils.windowsBuildServer("DocumentServer-IE")
                  }
                  if ( params.documentserver_de ) {
                    utils.windowsBuildServer("DocumentServer-DE")
                  }
                }
              }
            }
          }
        }
        stage('Windows 32-bit build') {
          agent {
            node {
              label 'win_32'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_32"
            }
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_32 ) {
                String platform = "win_32"
                utils.windowsBuild(platform, params.clean, false)
                if ( params.core ) {
                  utils.windowsBuildCore()
                }
                if ( params.documentbuilder ) {
                  utils.windowsBuildBuilder(platform)
                }
                if ( params.desktopeditor ) {
                  utils.windowsBuildDesktop(platform)
                }
              }
            }
          }
        }
        stage('Windows XP 64-bit build') {
          agent {
            node {
              label 'win_64_xp'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_64_xp"
            }
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_64_xp ) {
                String platform = "win_64_xp"
                utils.windowsBuild(platform, params.clean, false)
                if ( params.desktopeditor ) {
                  utils.windowsBuildDesktop(platform)
                }
              }
            }
          }
        }
        stage('Windows XP 32-bit build') {
          agent {
            node {
              label 'win_32_xp'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_32_xp"
            }
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_32_xp ) {
                String platform = "win_32_xp"
                utils.windowsBuild(platform, params.clean, false)
                if ( params.desktopeditor ) {
                  utils.windowsBuildDesktop(platform)
                }
              }
            }
          }
        }
      }
    }
  }
}
