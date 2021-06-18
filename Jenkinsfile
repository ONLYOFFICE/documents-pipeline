defaults = [
  clean:         true,
  linux:         true,
  macos_64:      true,
  macos_86:      false,
  windows_64:    true,
  windows_32:    true,
  windows_64_xp: true,
  windows_32_xp: true,
  android:       true,
  core:          true,
  editors:       true,
  builder:       true,
  server_ce:     true,
  server_ee:     true,
  server_ie:     true,
  server_de:     true,
  beta:          false,
  test:          false,
  sign:          true,
  schedule:      'H 17 * * *'
]

if (BRANCH_NAME == 'develop') {
  defaults.putAll([
    macos_64:      false,
    macos_86:      false,
    android:       false,
    core:          false,
    builder:       false,
    server_ce:     false,
    server_ie:     false,
    server_de:     false,
    beta:          true
  ])
}

if (BRANCH_NAME ==~ /^(hotfix|release)\/.+/) {
  defaults.schedule = 'H 23 * * *'
}

node('master') {
  checkout scm
  utils = load 'utils.groovy'
}

pipeline {
  agent none
  environment {
    TELEGRAM_TOKEN = credentials('telegram-bot-token')
  }
  options {
    buildDiscarder logRotator(daysToKeepStr: '90', artifactDaysToKeepStr: '30')
  }
  parameters {
    booleanParam (
      name:         'wipe',
      description:  'Wipe out current workspace',
      defaultValue: false
    )
    booleanParam (
      name:         'clean',
      description:  'Rebuild binaries from the \'core\' repo',
      defaultValue: defaults.clean
    )
    booleanParam (
      name:         'linux_64',
      description:  'Build Linux x64 targets',
      defaultValue: defaults.linux
    )
    booleanParam (
      name:         'macos_64',
      description:  'Build macOS targets',
      defaultValue: defaults.macos_64
    )
    booleanParam (
      name:         'macos_86',
      description:  'Build macOS x86 targets',
      defaultValue: defaults.macos_86
    )
    booleanParam (
      name:         'win_64',
      description:  'Build Windows x64 targets',
      defaultValue: defaults.windows_64
    )
    booleanParam (
      name:         'win_32',
      description:  'Build Windows x86 targets',
      defaultValue: defaults.windows_32
    )
    booleanParam (
      name:         'win_64_xp',
      description:  'Build Windows XP x64 targets',
      defaultValue: defaults.windows_64_xp
    )
    booleanParam (
      name:         'win_32_xp',
      description:  'Build Windows XP x86 targets',
      defaultValue: defaults.windows_32_xp
    )
    booleanParam (
      name:         'android',
      description:  'Build Android targets',
      defaultValue: defaults.android
    )
    booleanParam (
      name:         'core',
      description:  'Build and publish "core" binaries',
      defaultValue: defaults.core
    )
    booleanParam (
      name:         'editors',
      description:  'Build and publish DesktopEditors packages',
      defaultValue: defaults.editors
    )
    booleanParam (
      name:         'builder',
      description:  'Build and publish DocumentBuilder packages',
      defaultValue: defaults.builder
    )
    booleanParam (
      name:         'server_ce',
      description:  'Build and publish DocumentServer packages',
      defaultValue: defaults.server_ce
    )
    booleanParam (
      name:         'server_ee',
      description:  'Build and publish DocumentServer-EE packages',
      defaultValue: defaults.server_ee
    )
    booleanParam (
      name:         'server_ie',
      description:  'Build and publish DocumentServer-IE packages',
      defaultValue: defaults.server_ie
    )
    booleanParam (
      name:         'server_de',
      description:  'Build and publish DocumentServer-DE packages',
      defaultValue: defaults.server_de
    )
    booleanParam (
      name:         'beta',
      description:  'Beta (enabled anyway on develop)',
      defaultValue: defaults.beta
    )
    booleanParam (
      name:         'test',
      description:  'Run test(Only on Linux)',
      defaultValue: defaults.test
    )
    booleanParam (
      name:         'signing',
      description:  'Sign installer(Only on Windows)',
      defaultValue: defaults.sign
    )
    string (
      name:         'extra_args',
      description:  'configure.py extra args',
      defaultValue: ''
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
          if (pV.find()) productVersion = pV.group(2)
          env.PRODUCT_VERSION = productVersion

          env.S3_BUCKET = 'repo-doc-onlyoffice-com'
          env.RELEASE_BRANCH = branchName == 'develop' ? 'unstable' : 'testing'

          if (params.signing) env.ENABLE_SIGNING=1

          deployDesktopList = []
          deployBuilderList = []
          deployServerCeList = []
          deployServerEeList = []
          deployServerDeList = []
          deployAndroidList = []
          stageStats = [:]
        }
      }
      post {
        fixed   { script { utils.setStageStats('fixed')   } }
        failure { script { utils.setStageStats('failure') } }
        success { script { utils.setStageStats('success') } }
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
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "linux_64"

              if (params.core || params.builder || params.server_ce) {
                utils.linuxBuild(platform)
                if (params.core)
                  utils.deployCore(platform)
                if (params.builder)
                  utils.linuxBuildBuilder(platform)
                if (params.server_ce)
                  utils.linuxBuildServer(platform)
              }

              if (params.editors) {
                utils.linuxBuild(platform, "freemium")
                utils.linuxBuildDesktop(platform)
              }

              if (params.server_ee || params.server_ie || params.server_de) {
                utils.linuxBuild(platform, "commercial")
                if (params.server_ee)
                  utils.linuxBuildServer(platform, "documentserver-ee")
                if (params.server_ie)
                  utils.linuxBuildServer(platform, "documentserver-ie")
                if (params.server_ee || params.server_ie)
                  utils.tagRepos("v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
                if (params.server_de)
                  utils.linuxBuildServer(platform, "documentserver-de")
              }
              if (params.test) utils.linuxTest()
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
          }
        }
        stage('macOS build') {
          agent { label 'macos_64' }
          environment {
            FASTLANE_DISABLE_COLORS = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          when {
            expression { params.macos_64 }
            beforeAgent true
          }
          steps {
            script {
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_64"

              if (params.core) {
                utils.macosBuild(platform)
                utils.deployCore(platform)
              }

              if (params.editors) {
                utils.macosBuild(platform, "freemium")
                utils.macosBuildDesktop()
              }
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
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
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_64"

              if (params.editors) {
                utils.macosBuild(platform, "freemium")
                utils.macosBuildDesktop()
              }
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
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
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_64"

              if (params.core || params.builder || params.server_ce) {
                utils.windowsBuild(platform)
                if (params.core)
                  utils.deployCore(platform)
                if (params.builder)
                  utils.windowsBuildBuilder(platform)
                if (params.server_ce)
                  utils.windowsBuildServer(platform)
              }

              if (params.editors) {
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }

              if (params.server_ee || params.server_ie || params.server_de) {
                utils.windowsBuild(platform, "commercial")
                if (params.server_ee)
                  utils.windowsBuildServer(platform, "DocumentServer-EE")
                if (params.server_ie)
                  utils.windowsBuildServer(platform, "DocumentServer-IE")
                if (params.server_de)
                  utils.windowsBuildServer(platform, "DocumentServer-DE")
              }
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
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
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_32"

              if (params.core || params.builder) {
                utils.windowsBuild(platform)
                if (params.core)
                  utils.deployCore(platform)
                if (params.builder)
                  utils.windowsBuildBuilder(platform)
              }

              if (params.editors) {
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
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
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_64_xp"
              if (params.editors) {
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
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
              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_32_xp"
              if (params.editors) {
                utils.windowsBuild(platform, "freemium")
                utils.windowsBuildDesktop(platform)
              }
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
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
              if (params.wipe) deleteDir()

              utils.androidBuild(env.BRANCH_NAME)
            }
          }
          post {
            fixed   { script { utils.setStageStats('fixed')   } }
            failure { script { utils.setStageStats('failure') } }
            success { script { utils.setStageStats('success') } }
          }
        }
      }
    }
  }
  post {
    always {
      node('master') {
        script {
          utils.createReports()
        }
      }
      script {
        if (params.linux_64
          && (params.editors
          || params.builder
          || params.server_ce
          || params.server_ee
          || params.server_ie
          || params.server_de)) {
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
    fixed {
      node('master') {
        script {
          utils.sendTelegramMessage(utils.getJobStats('fixed'), '-342815292')
        }
      }
    }
    failure {
      node('master') {
        script {
          utils.sendTelegramMessage(utils.getJobStats('failed'), '-342815292')
        }
      }
    }
  }
}
