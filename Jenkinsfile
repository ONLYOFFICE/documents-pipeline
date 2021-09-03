defaults = [
  branch:        'experimental',
  version:       '99.99.99',
  clean:         true,
  linux:         true,
  macos_64:      true,
  macos_64_v8:   true,
  macos_arm64:   true,
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
  server_de:     true,
  beta:          false,
  test:          false,
  sign:          true,
  schedule:      'H 17 * * *'
]

if (BRANCH_NAME == 'develop') {
  defaults.putAll([
    branch:        'unstable',
    macos_64:      false,
    macos_64_v8:   false,
    macos_arm64:   false,
    android:       false,
    builder:       false,
    server_ce:     false,
    server_de:     false,
    beta:          true
  ])
}

if (BRANCH_NAME ==~ /^(hotfix|release)\/.+/) {
  defaults.putAll([
    branch:        'testing',
    version:       BRANCH_NAME.replaceAll(/.+\/v(?=[0-9.]+)/,''),
    schedule:      'H 23 * * *'
  ])
}

node('master') {
  checkout scm
  utils = load 'utils.groovy'
}

pipeline {
  agent none
  environment {
    COMPANY_NAME = "ONLYOFFICE"
    RELEASE_BRANCH = "${defaults.branch}"
    PRODUCT_VERSION = "${defaults.version}"
    TELEGRAM_TOKEN = credentials('telegram-bot-token')
    S3_BUCKET = "repo-doc-onlyoffice-com"
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
      description:  'Build macOS x86-64 targets',
      defaultValue: defaults.macos_64
    )
    booleanParam (
      name:         'macos_64_v8',
      description:  'Build macOS x86-64 V8 targets',
      defaultValue: defaults.macos_64_v8
    )
    booleanParam (
      name:         'macos_arm64',
      description:  'Build macOS ARM64 targets',
      defaultValue: defaults.macos_arm64
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
          if (params.signing) env.ENABLE_SIGNING=1

          s3region = "eu-west-1"
          s3bucket = "repo-doc-onlyoffice-com"
          s3deploy = "${s3bucket}/${env.COMPANY_NAME.toLowerCase()}/${env.RELEASE_BRANCH}"
          listDeploy = []
          stageStats = [:]
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
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "linux_64"

              if (params.core || params.builder || params.server_ce) {
                utils.build(platform)
                if (params.builder)   utils.buildBuilder(platform)
                if (params.server_ce) utils.buildServer(platform)
              }

              if (params.editors || params.server_ee || params.server_de) {
                utils.build(platform, "commercial")

                if (params.editors) {
                  utils.buildEditors(platform)
                }
                if (params.server_ee) {
                  utils.buildServer(platform, "enterprise")
                  utils.tagRepos("v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
                }
                if (params.server_de)
                  utils.buildServer(platform, "developer")
              }
              if (params.test) utils.linuxTest()

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('macOS 64-bit build') {
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
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_64"

              if (params.core)
                utils.build(platform)

              if (params.editors) {
                utils.build(platform, "commercial")
                utils.buildEditors(platform)
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('macOS 64-bit V8 build') {
          agent { label 'macos_64_v8' }
          environment {
            FASTLANE_DISABLE_COLORS = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
            USE_V8 = '1'
          }
          when {
            expression { params.macos_64_v8 }
            beforeAgent true
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_64"

              if (params.editors) {
                utils.build(platform, "commercial")
                utils.buildEditors(platform)
              }

              stageStats."${STAGE_NAME}" = true              
            }
          }
        }
        stage('macOS ARM64 build') {
          agent { label 'macos_arm64' }
          environment {
            FASTLANE_DISABLE_COLORS = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          when {
            expression { params.macos_arm64 }
            beforeAgent true
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "mac_arm64"

              if (params.editors) {
                utils.build(platform, "commercial")
                utils.buildEditors(platform)
              }

              stageStats."${STAGE_NAME}" = true              
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
          when {
            expression { params.win_64 }
            beforeAgent true
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_64"

              if (params.core || params.builder || params.server_ce) {
                utils.build(platform)
                if (params.builder)   utils.buildBuilder(platform)
                if (params.server_ce) utils.buildServer(platform)
              }

              if (params.editors || params.server_ee || params.server_de) {
                utils.build(platform, "commercial")
				if (params.editors)   utils.buildEditors(platform)
                if (params.server_ee) utils.buildServer(platform, "enterprise")
                if (params.server_de) utils.buildServer(platform, "developer")
              }

              stageStats."${STAGE_NAME}" = true
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
          when {
            expression { params.win_32 }
            beforeAgent true
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_32"

              if (params.core || params.builder) {
                utils.build(platform)
                if (params.builder) utils.buildBuilder(platform)
              }

              if (params.editors) {
                utils.build(platform, "commercial")
                utils.buildEditors(platform)
              }

              stageStats."${STAGE_NAME}" = true
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
          when {
            expression { params.win_64_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_64_xp"

              if (params.editors) {
                utils.build(platform, "commercial")
                utils.buildEditors(platform)
              }

              stageStats."${STAGE_NAME}" = true
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
          when {
            expression { params.win_32_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.editors)
                dir ('desktop-apps') { deleteDir() }

              utils.checkoutRepos(env.BRANCH_NAME)

              String platform = "win_32_xp"

              if (params.editors) {
                utils.build(platform, "commercial")
                utils.buildEditors(platform)
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('Android build') {
          agent { label 'android' }
          when {
            expression { params.android && params.core }
            beforeAgent true
          }
          steps {
            script {
              stageStats."${STAGE_NAME}" = false

              if (params.wipe) deleteDir()

              utils.buildAndroid(env.BRANCH_NAME)

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
      }
    }
  }
  post {
    always {
      node('master') {
        script {
          utils.generateReports()
        }
      }
      script {
        if (params.linux_64)
          build (
            job: 'repo-manager',
            parameters: [
              string (name: 'company', value: env.COMPANY_NAME.toLowerCase()),
              string (name: 'branch', value: env.RELEASE_BRANCH)
            ],
            wait: false
          )
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
