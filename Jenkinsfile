pipeline {
  agent none
  parameters {
    booleanParam (
      defaultValue: false,
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
      defaultValue: false,
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
      defaultValue: false,
      description: 'Run test(Only on Linux)',
      name: 'test'
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
        }
        script {
          env.COMPANY_NAME = "R7-Office"
          env.PUBLISHER_NAME = "AO \"NOVYE KOMMUNIKACIONNYE TEHNOLOGII\""
          env.PUBLISHER_URL = "http://r7-office.ru"
          env.SUPPORT_URL = "http://support.r7-office.ru"
          env.SUPPORT_MAIL = "support@r7-office.ru"
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
                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "linux_64"
                Boolean clean = params.clean
                if ( params.core
                     || params.documentbuilder
                     || params.desktopeditor
                     || params.documentserver
                     ) {
                  utils.linuxBuild(platform, clean, true)
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
                    /*
                    utils.tagRepos("v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
                    */
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
              customWorkspace "C:\\r7\\${env.BRANCH_NAME}\\win_64"
            }
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_64 ) {
                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "win_64"
                utils.windowsBuild(platform, params.clean, true)
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
              customWorkspace "C:\\r7\\${env.BRANCH_NAME}\\win_32"
            }
          }
          steps {
            script {
              def utils = load "utils.groovy"
              if ( params.win_32 ) {
                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "win_32"
                utils.windowsBuild(platform, params.clean, true)
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
                utils.checkoutRepos(env.BRANCH_NAME)

                String platform = "win_64_xp"
                utils.windowsBuild(platform, params.clean, true)
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
