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
      defaultValue: false,
      description: 'Build and publish DocumentBuilder packages',
      name: 'documentbuilder'
    )
    booleanParam (
      defaultValue: false,
      description: 'Build and publish DocumentServer packages',
      name: 'documentserver'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build and publish DocumentServer-IE packages',
      name: 'documentserver_ie'
    )
    booleanParam (
      defaultValue: false,
      description: 'Build and publish DocumentServer-DE packages',
      name: 'documentserver_de'
    )
    booleanParam (
      defaultValue: false,
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
          def productVersion = "5.5.99"
          def pV = branchName =~ /^(release|hotfix)\\/v(.*)$/
          if(pV.find()) {
            productVersion = pV.group(2)
          }
          env.PRODUCT_VERSION = productVersion

          if( params.signing ) {
            env.ENABLE_SIGNING=1
          }
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
          when {
            expression { params.linux_64 }
            beforeAgent true
          }
          steps {
            script {
              def utils = load "utils.groovy"
              
              if ( params.wipe ) {
                deleteDir()
              }

              if ( params.clean && params.desktopeditor ) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "linux_64"
              Boolean clean = params.clean

              if ( params.core
                   || params.documentbuilder
                   || params.documentserver
                   ) {
                utils.linuxBuild(platform, clean)
                clean = false
                if ( params.core ) {
                  utils.linuxBuildCore()
                }
                if ( params.documentbuilder ) {
                  utils.linuxBuildBuilder(platform)
                }
                if ( params.documentserver ) {
                  utils.linuxBuildServer(platform)
                }
              }

              if ( params.desktopeditor ) {
                utils.linuxBuild(platform, clean, "freemium")
                clean = false
                utils.linuxBuildDesktop(platform)
              }

              if ( params.documentserver_ie || params.documentserver_de ) {
                utils.linuxBuild(platform, clean, "commercial")
                clean = false
                if ( params.documentserver_ie ) {
                  utils.linuxBuildServer(platform, "documentserver-ie")
                  /*
                  utils.tagRepos("v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
                  */
                }
                if ( params.documentserver_de ) {
                  utils.linuxBuildServer(platform, "documentserver-de")
                }
              }
              if ( params.test ) {
                utils.linuxTest()
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
          when {
            expression { params.win_64 }
            beforeAgent true
          }
          steps {
            script {
              def utils = load "utils.groovy"

              if ( params.wipe ) {
                deleteDir()
              }

              if ( params.clean && params.desktopeditor ) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_64"
              Boolean clean = params.clean

              if ( params.core
                   || params.documentbuilder
                   || params.documentserver
                   ) {
                utils.windowsBuild(platform, clean)
                clean = false
                if ( params.core ) {
                  utils.windowsBuildCore(platform)
                }
                if ( params.documentbuilder ) {
                  utils.windowsBuildBuilder(platform)
                }
                if ( params.documentserver ) {
                  utils.windowsBuildServer(platform)
                }
              }

              if ( params.desktopeditor ) {
                utils.windowsBuild(platform, clean, "freemium")
                clean = false
                utils.windowsBuildDesktop(platform)
              }

              if ( params.documentserver_ie || params.documentserver_de ) {
                utils.windowsBuild(platform, clean, "commercial")
                clean = false
                if ( params.documentserver_ie ) {
                  utils.windowsBuildServer(platform, "DocumentServer-IE")
                }
                if ( params.documentserver_de ) {
                  utils.windowsBuildServer(platform, "DocumentServer-DE")
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
          when {
            expression { params.win_32 }
            beforeAgent true
          }
          steps {
            script {
              def utils = load "utils.groovy"

              if ( params.wipe ) {
                deleteDir()
              }

              if ( params.clean && params.desktopeditor ) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_32"
              Boolean clean = params.clean

              if ( params.core || params.documentbuilder ) {
                utils.windowsBuild(platform, clean)
                clean = false
                if ( params.core ) {
                  utils.windowsBuildCore(platform)
                }
                if ( params.documentbuilder ) {
                  utils.windowsBuildBuilder(platform)
                }
              }

              if ( params.desktopeditor ) {
                utils.windowsBuild(platform, clean, "freemium")
                clean = false
                utils.windowsBuildDesktop(platform)
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
          when {
            expression { params.win_64_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              def utils = load "utils.groovy"

              if ( params.wipe ) {
                deleteDir()
              }

              if ( params.clean && params.desktopeditor ) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_64_xp"
              if ( params.desktopeditor ) {
                utils.windowsBuild(platform, params.clean, "freemium")
                utils.windowsBuildDesktop(platform)
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
          when {
            expression { params.win_32_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              def utils = load "utils.groovy"

              if ( params.wipe ) {
                deleteDir()
              }

              if ( params.clean && params.desktopeditor ) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_32_xp"
              if ( params.desktopeditor ) {
                utils.windowsBuild(platform, params.clean, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
        }
      }
    }
  }
}
