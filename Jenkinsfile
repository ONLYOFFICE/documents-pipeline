defaults = [:]
defaults.schedule = 'H 17 * * *'

if (BRANCH_NAME.startsWith('hotfix') || BRANCH_NAME.startsWith('release')) {
  defaults.schedule = 'H 23 * * *'
}

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
      description: 'Build macOS targets',
      name: 'macos'
    )
    booleanParam (
      defaultValue: true,
      description: 'Build macOS x86 targets',
      name: 'macos_86'
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
      description: 'Build Android targets',
      name: 'android'
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
      description: 'Build and publish DocumentServer-EE packages',
      name: 'documentserver_ee'
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
      description: 'Beta (enabled anyway on develop)',
      name: 'beta'
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
    cron(defaults.schedule)
  }
  stages {
    stage('Prepare') {
      steps {
        script {
          def branchName = env.BRANCH_NAME
          def productVersion = "99.99.99"
          def pV = branchName =~ /^(release|hotfix)\\/v(.*)$/
          if(pV.find()) {
            productVersion = pV.group(2)
          }
          env.PRODUCT_VERSION = productVersion

          env.S3_BUCKET = 'repo-doc-onlyoffice-com'
          env.RELEASE_BRANCH = branchName == 'develop' ? 'unstable' : 'testing'

          if( params.signing ) {
            env.ENABLE_SIGNING=1
          }

          tgMessageCore = "Build [${JOB_NAME}#${BUILD_NUMBER}](${BUILD_URL}) failed"
          deployDesktopList = []
          deployBuilderList = []
          deployServerCeList = []
          deployServerEeList = []
          deployServerDeList = []
          deployAndroidList = []
        }
      }
      post {
        success { script { tgMessageCore += "\n🔵 Prepare" } }
        failure { script { tgMessageCore += "\n🔴 Prepare" } }
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

              if ( params.core
                   || params.documentbuilder
                   || params.documentserver
                   ) {
                utils.linuxBuild(platform)
                if ( params.core ) {
                  utils.deployCore(platform)
                }
                if ( params.documentbuilder ) {
                  utils.linuxBuildBuilder(platform)
                }
                if ( params.documentserver ) {
                  utils.linuxBuildServer(platform)
                }
              }

              if ( params.desktopeditor ) {
                utils.linuxBuild(platform, "freemium")
                utils.linuxBuildDesktop(platform)
              }

              if ( params.documentserver_ee || params.documentserver_ie || params.documentserver_de ) {
                utils.linuxBuild(platform, "commercial")
                if ( params.documentserver_ee ) {
                  utils.linuxBuildServer(platform, "documentserver-ee")
                }
                if ( params.documentserver_ie ) {
                  utils.linuxBuildServer(platform, "documentserver-ie")
                }
                if ( params.documentserver_ee || params.documentserver_ie ) {
                  utils.tagRepos("v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
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
          post {
            success { script { tgMessageCore += "\n🔵 Linux 64-bit" } }
            failure { script { tgMessageCore += "\n🔴 Linux 64-bit" } }
          }
        }
        stage('macOS build') {
          agent { label 'macos' }
          environment {
            FASTLANE_DISABLE_COLORS = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          when {
            expression { params.macos }
            beforeAgent true
          }
          steps {
            script {
              def utils = load "utils.groovy"
              
              if (params.wipe) {
                deleteDir()
              } else if (params.clean && params.desktopeditor) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_64"

              if (params.core) {
                utils.macosBuild(platform)
                utils.deployCore(platform)
              }

              if (params.desktopeditor) {
                utils.macosBuild(platform, "freemium")
                utils.macosBuildDesktop()
              }
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 macOS" } }
            failure { script { tgMessageCore += "\n🔴 macOS" } }
          }
        }
        stage('macOS x86 build') {
          agent { label 'macos_86' }
          environment {
            FASTLANE_DISABLE_COLORS = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
            _X86 = '1'
          }
          when {
            expression { params.macos_86 }
            beforeAgent true
          }
          steps {
            script {
              def utils = load "utils.groovy"
              
              if (params.wipe) {
                deleteDir()
              } else if (params.clean && params.desktopeditor) {
                dir (utils.getReposList().find { it.name == 'desktop-apps' }.dir) {
                  deleteDir()
                }
              }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_64"

              if (params.desktopeditor) {
                utils.macosBuild(platform, "freemium")
                utils.macosBuildDesktop()
              }
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 macOS x86" } }
            failure { script { tgMessageCore += "\n🔴 macOS x86" } }
          }
        }
        stage('Windows 64-bit build') {
          agent {
            node {
              label 'win_64'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_64"
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

              if ( params.core
                   || params.documentbuilder
                   || params.documentserver
                   ) {
                utils.windowsBuild(platform)
                if ( params.core ) {
                  utils.deployCore(platform)
                }
                if ( params.documentbuilder ) {
                  utils.windowsBuildBuilder(platform)
                }
                if ( params.documentserver ) {
                  utils.windowsBuildServer(platform)
                }
              }

              if ( params.desktopeditor ) {
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }

              if ( params.documentserver_ee || params.documentserver_ie || params.documentserver_de ) {
                utils.windowsBuild(platform, "commercial")
                if ( params.documentserver_ee ) {
                  utils.windowsBuildServer(platform, "DocumentServer-EE")
                }
                if ( params.documentserver_ie ) {
                  utils.windowsBuildServer(platform, "DocumentServer-IE")
                }
                if ( params.documentserver_de ) {
                  utils.windowsBuildServer(platform, "DocumentServer-DE")
                }
              }
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 Windows 64-bit" } }
            failure { script { tgMessageCore += "\n🔴 Windows 64-bit" } }
          }
        }
        stage('Windows 32-bit build') {
          agent {
            node {
              label 'win_32'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_32"
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

              if ( params.core || params.documentbuilder ) {
                utils.windowsBuild(platform)
                if ( params.core ) {
                  utils.deployCore(platform)
                }
                if ( params.documentbuilder ) {
                  utils.windowsBuildBuilder(platform)
                }
              }

              if ( params.desktopeditor ) {
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 Windows 32-bit" } }
            failure { script { tgMessageCore += "\n🔴 Windows 32-bit" } }
          }
        }
        stage('Windows XP 64-bit build') {
          agent {
            node {
              label 'win_64_xp'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_64_xp"
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
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 Windows XP 64-bit" } }
            failure { script { tgMessageCore += "\n🔴 Windows XP 64-bit" } }
          }
        }
        stage('Windows XP 32-bit build') {
          agent {
            node {
              label 'win_32_xp'
              customWorkspace "C:\\oo\\${env.BRANCH_NAME}\\win_32_xp"
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
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 Windows XP 32-bit" } }
            failure { script { tgMessageCore += "\n🔴 Windows XP 32-bit" } }
          }
        }
        stage('Android build') {
          agent { label 'linux_64' }
          when {
            expression { params.android && params.core }
            beforeAgent true
          }
          steps {
            script {
              def utils = load "utils.groovy"
              
              if (params.wipe) { deleteDir() }

              utils.androidBuild(env.BRANCH_NAME)
            }
          }
          post {
            success { script { tgMessageCore += "\n🔵 Android" } }
            failure { script { tgMessageCore += "\n🔴 Android" } }
          }
        }
      }
    }
  }
  post {
    always {
      node('master') {
        script {
          checkout scm
          def utils = load "utils.groovy"
          utils.createReports()
        }
      }
      script {
        if (params.linux_64 && (
            params.desktopeditor ||
            params.documentbuilder ||
            params.documentserver_ee ||
            params.documentserver_ie ||
            params.documentserver_de)
        ) {
          build (
            job: 'onlyoffice-repo-manager',
            parameters: [
              string (name: 'release_branch', value: env.RELEASE_BRANCH)
            ],
            wait: false
          )
        }
      }
    }
    failure {
      node('master') {
        telegramSend(message: tgMessageCore, chatId: -342815292)
      }
    }
  }
}
