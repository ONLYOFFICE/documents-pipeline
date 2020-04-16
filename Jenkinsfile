pipeline {
  agent none
  parameters {
    booleanParam (
      defaultValue: false,
      description: 'Wipe out current workspace',
      name: 'wipe'
    )
    booleanParam (
      defaultValue: true,
      description: 'Rebuild binaries from the \'core\' repo',
      name: 'clean'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build Linux x64 targets',
      name: 'linux_64'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build Windows x64 targets',
      name: 'win_64'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build Windows x86 targets',
      name: 'win_32'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build Windows XP x64 targets',
      name: 'win_64_xp'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build Windows XP x86 targets',
      name: 'win_32_xp'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish \'core\' binaries',
      name: 'core'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish DesktopEditors packages',
      name: 'desktopeditor'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish DocumentBuilder packages',
      name: 'documentbuilder'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish DocumentServer packages',
      name: 'documentserver'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish DocumentServer-IE packages',
      name: 'documentserver_ie'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish DocumentServer-DE packages',
      name: 'documentserver_de'
    )
    booleanParam (
      defaultValue: true,
      description: 'Run test(Only on Linux)',
      name: 'test'
    )
    booleanParam (
      defaultValue: true,
      description: 'Sign installer(Only on Windows)',
      name: 'signing'
    )
    string (
      defaultValue: '',
      description: 'configure.py extra params',
      name: 'extra_params'
    )
  }
  triggers {
    cron('H 17 * * *')
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

          if( params.signing ) {
            env.ENABLE_SIGNING=1
          }
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
                if ( params.wipe ) {
                  deleteDir()
                }

                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "linux_64"
                Boolean clean = params.clean
                if ( params.core
                     || params.documentbuilder
                     || params.desktopeditor
                     || params.documentserver
                     ) {
                  utils.linuxBuild(platform, clean, false)
                  clean = false
                }
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
                if ( params.documentserver_ie || params.documentserver_de ) {
                  utils.linuxBuild(platform, clean, true)
                  if ( params.documentserver_ie ) {
                    utils.linuxBuildServer("documentserver-ie")
                    utils.tagRepos("v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
                  }
                  if ( params.documentserver_de ) {
                    utils.linuxBuildServer("documentserver-de")
                  }
                }
                if ( params.test ) {
                  utils.linuxTest()
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
                if ( params.wipe ) {
                  deleteDir()
                }

                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "win_64"
                utils.windowsBuild(platform, params.clean, false)
                if ( params.core ) {
                  utils.windowsBuildCore(platform)
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
                if ( params.wipe ) {
                  deleteDir()
                }

                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "win_32"
                utils.windowsBuild(platform, params.clean, false)
                if ( params.core ) {
                  utils.windowsBuildCore(platform)
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
                if ( params.wipe ) {
                  deleteDir()
                }

                utils.checkoutRepos(env.BRANCH_NAME)

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
                if ( params.wipe ) {
                  deleteDir()
                }

                utils.checkoutRepos(env.BRANCH_NAME)

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
