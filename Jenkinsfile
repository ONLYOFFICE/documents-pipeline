defaults = [
  branch:          'experimental',
  version:         '99.99.99',
  clean:           true,
  windows_x64:     true,
  windows_x86:     true,
  windows_x64_xp:  true,
  windows_x86_xp:  true,
  macos_x86_64:    true,
  macos_x86_64_v8: true,
  macos_arm64:     true,
  linux_x86_64:    true,
  linux_aarch64:   true,
  android:         true,
  core:            true,
  desktop:         true,
  builder:         true,
  server_ce:       true,
  server_ee:       true,
  server_de:       true,
  mobile:          true,
  beta:            false,
  test:            false,
  sign:            true,
  schedule:        'H 17 * * *'
]

if (BRANCH_NAME == 'develop') {
  defaults.putAll([
    branch:          'unstable',
    macos_x86_64:    false,
    macos_x86_64_v8: false,
    macos_arm64:     false,
    android:         false,
    server_ce:       false,
    server_de:       false,
    mobile:          false,
    beta:            true
  ])
}

if (BRANCH_NAME ==~ /^(hotfix|release)\/.+/) {
  defaults.putAll([
    branch:          'testing',
    version:         BRANCH_NAME.replaceAll(/.+\/v(?=[0-9.]+)/,''),
    schedule:        'H 23 * * *'
  ])
}

pipeline {
  agent none
  environment {
    COMPANY_NAME = "ONLYOFFICE"
    RELEASE_BRANCH = "${defaults.branch}"
    PRODUCT_VERSION = "${defaults.version}"
    TELEGRAM_TOKEN = credentials('telegram-bot-token')
    CODESIGN_CERT_PWD = credentials('codesign-cert-pwd')
    S3_BUCKET = "repo-doc-onlyoffice-com"
  }
  options {
    checkoutToSubdirectory 'documents-pipeline'
    buildDiscarder logRotator(daysToKeepStr: '30', artifactDaysToKeepStr: '30')
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
    // Windows
    booleanParam (
      name:         'windows_x64',
      description:  'Build Windows x64 targets (Visual Studio 2019)',
      defaultValue: defaults.windows_x64
    )
    booleanParam (
      name:         'windows_x86',
      description:  'Build Windows x86 targets (Visual Studio 2019)',
      defaultValue: defaults.windows_x86
    )
    booleanParam (
      name:         'windows_x64_xp',
      description:  'Build Windows XP x64 targets (Visual Studio 2015)',
      defaultValue: defaults.windows_x64_xp
    )
    booleanParam (
      name:         'windows_x86_xp',
      description:  'Build Windows XP x86 targets (Visual Studio 2015)',
      defaultValue: defaults.windows_x86_xp
    )
    // macOS
    booleanParam (
      name:         'macos_x86_64',
      description:  'Build macOS x86-64 targets',
      defaultValue: defaults.macos_x86_64
    )
    booleanParam (
      name:         'macos_x86_64_v8',
      description:  'Build macOS V8 x86-64 targets',
      defaultValue: defaults.macos_x86_64_v8
    )
    booleanParam (
      name:         'macos_arm64',
      description:  'Build macOS ARM64 targets',
      defaultValue: defaults.macos_arm64
    )
    // Linux
    booleanParam (
      name:         'linux_x86_64',
      description:  'Build Linux x86-64 targets',
      defaultValue: defaults.linux_x86_64
    )
    booleanParam (
      name:         'linux_aarch64',
      description:  'Build Linux aarch64 targets',
      defaultValue: defaults.linux_aarch64
    )
    // Android
    booleanParam (
      name:         'android',
      description:  'Build Android targets',
      defaultValue: defaults.android
    )
    // Modules
    booleanParam (
      name:         'core',
      description:  'Build and publish "core" binaries',
      defaultValue: defaults.core
    )
    booleanParam (
      name:         'desktop',
      description:  'Build and publish DesktopEditors packages',
      defaultValue: defaults.desktop
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
      name:         'mobile',
      description:  'Build and publish Mobile libraries',
      defaultValue: defaults.mobile
    )
    // Other
    booleanParam (
      name:         'beta',
      description:  'Beta (enabled anyway on develop)',
      defaultValue: defaults.beta
    )
    booleanParam (
      name:         'test',
      description:  'Run test (Only on Linux)',
      defaultValue: defaults.test
    )
    booleanParam (
      name:         'signing',
      description:  'Sign installer (Only on Windows)',
      defaultValue: defaults.sign
    )
    string (
      name:         'extra_args',
      description:  'configure.py extra args',
      defaultValue: ''
    )
    booleanParam (
      name:         'notify',
      description:  'Telegram notification',
      defaultValue: true
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
          branchDir = env.BRANCH_NAME.replaceAll(/\//,'_')
          listDeploy = []
          stageStats = [:]
        }
      }
    }
    stage('Build') {
      parallel {
        // Windows
        stage('Windows x64') {
          agent {
            node {
              label 'windows_x64'
              customWorkspace "C:\\oo\\${branchDir}_x64"
            }
          }
          when {
            expression { params.windows_x64 }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "win_64"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.core || params.builder || params.server_ce) {
                build(platform)
                if (params.builder)   buildBuilder(platform)
                if (params.server_ce) buildServer(platform)
              }

              if (params.desktop || params.server_ee || params.server_de) {
                build(platform, "commercial")
                if (params.desktop)   buildDesktop(platform)
                if (params.server_ee) buildServer(platform, "enterprise")
                if (params.server_de) buildServer(platform, "developer")
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('Windows x86') {
          agent {
            node {
              label 'windows_x86'
              customWorkspace "C:\\oo\\${branchDir}_x86"
            }
          }
          when {
            expression { params.windows_x86 }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "win_32"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.core) {
                build(platform)
              }

              if (params.desktop) {
                build(platform, "commercial")
                buildDesktop(platform)
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('Windows x64 XP') {
          agent {
            node {
              label 'win_64_xp'
              customWorkspace "C:\\oo\\${branchDir}_x64_xp"
            }
          }
          when {
            expression { params.windows_x64_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "win_64_xp"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.desktop) {
                build(platform, "commercial")
                buildDesktop(platform)
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('Windows x86 XP') {
          agent {
            node {
              label 'win_32_xp'
              customWorkspace "C:\\oo\\${branchDir}_x86_xp"
            }
          }
          when {
            expression { params.windows_x86_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "win_32_xp"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.desktop) {
                build(platform, "commercial")
                buildDesktop(platform)
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        // macOS
        stage('macOS x64') {
          agent { label 'macos_64' }
          environment {
            FASTLANE_HIDE_TIMESTAMP = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          when {
            expression { params.macos_x86_64 }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "mac_64"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.core)
                build(platform)

              if (params.desktop) {
                build(platform, "commercial")
                buildDesktop(platform)
              }

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('macOS x64 V8') {
          agent { label 'macos_64_v8' }
          environment {
            FASTLANE_HIDE_TIMESTAMP = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
            USE_V8 = '1'
          }
          when {
            expression { params.macos_x86_64_v8 }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "mac_64"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.desktop) {
                build(platform, "commercial")
                buildDesktop(platform)
              }

              stageStats."${STAGE_NAME}" = true              
            }
          }
        }
        stage('macOS ARM64') {
          agent { label 'macos_arm64' }
          environment {
            FASTLANE_HIDE_TIMESTAMP = '1'
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
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "mac_arm64"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              checkoutRepos(varRepos)

              if (params.desktop) {
                build(platform, "commercial")
                buildDesktop(platform)
              }

              stageStats."${STAGE_NAME}" = true              
            }
          }
        }
        // Linux
        stage('Linux x64') {
          agent { label 'linux_64' }
          when {
            expression { params.linux_x86_64 }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "linux_64"
              ArrayList constRepos = getConstRepos(env.BRANCH_NAME)
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              ArrayList allRepos = constRepos.plus(varRepos)
              checkoutRepos(varRepos)

              if (params.core || params.builder || params.server_ce) {
                build(platform)
                if (params.builder)   buildBuilder(platform)
                if (params.server_ce) buildServer(platform)
              }

              if (params.desktop || params.server_ee || params.server_de) {
                build(platform, "commercial")

                if (params.desktop) {
                  buildDesktop(platform)
                }
                if (params.server_ee) {
                  buildServer(platform, "enterprise")
                  tagRepos(allRepos, "v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}")
                }
                if (params.server_de)
                  buildServer(platform, "developer")
              }
              if (params.test) linuxTest()

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        stage('Linux ARM64') {
          agent { label 'linux_64_ubuntu20' }
          when {
            expression { params.linux_aarch64 }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe)
                deleteDir()
              else if (params.clean && params.desktop)
                dir ('desktop-apps') { deleteDir() }

              String platform = "linux_arm64"
              ArrayList constRepos = getConstRepos(env.BRANCH_NAME)
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, "onlyoffice")
              ArrayList allRepos = constRepos.plus(varRepos)
              checkoutRepos(varRepos)

              if (params.core || params.builder || params.server_ce) {
                build(platform)
                // if (params.builder)   buildBuilder(platform)
                // if (params.server_ce) buildServer(platform)
              }

              if (params.desktop || params.server_ee || params.server_de) {
                build(platform, "commercial")

              //   if (params.desktop) {
              //     buildDesktop(platform)
              //   }
              //   if (params.server_ee) {
              //     buildServer(platform, "enterprise")
              //   }
              //   if (params.server_de)
              //     buildServer(platform, "developer")
              }
              // if (params.test) linuxTest()

              stageStats."${STAGE_NAME}" = true
            }
          }
        }
        // Android
        stage('Android') {
          agent { label 'android_ubuntu20' }
          when {
            expression { params.android && params.mobile }
            beforeAgent true
          }
          steps {
            script {
              echo "NODE_NAME=" + env.NODE_NAME
              stageStats."${STAGE_NAME}" = false

              if (params.wipe) deleteDir()
              sh "rm -rfv *.zip"

              String platform = "android"
              ArrayList varRepos = getVarRepos(env.BRANCH_NAME, platform, null)
              checkoutRepos(varRepos)

              if (params.mobile) {
                build(platform)
                buildAndroid(env.BRANCH_NAME)
              }

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
          generateReports()
        }
      }
      script {
        if (params.linux_x86_64 || params.linux_aarch64)
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
          sendTelegramMessage(getJobStats('fixed'), '-1001773122025')
        }
      }
    }
    failure {
      node('master') {
        script {
          sendTelegramMessage(getJobStats('failed'), '-1001773122025')
        }
      }
    }
  }
}

void checkoutRepo(String repo, String branch = 'master', String dir = repo.minus(~/^.+\//)) {
  if (dir == null) dir = repo.minus(~/^.+\//)
  def retryCount = 0
  retry(3) {
    if (retryCount > 0) sleep(30)
    checkout([
      $class: 'GitSCM',
      branches: [[name: 'refs/heads/' + branch]],
      doGenerateSubmoduleConfigurations: false,
      extensions: [
        [$class: 'SubmoduleOption', recursiveSubmodules: true],
        [$class: 'RelativeTargetDirectory', relativeTargetDir: dir],
        [$class: 'ScmName', name: "${repo}"]
      ],
      submoduleCfg: [],
      userRemoteConfigs: [[url: "git@github.com:${repo}.git"]]
    ])
    retryCount++
  }
}

def getConstRepos(String branch = 'master') {
  return [
    [owner: "ONLYOFFICE", name: "build_tools"],
    [owner: "ONLYOFFICE", name: "onlyoffice" ]
  ].each {
    it.branch = branch
    it.dir = it.name
  }
}

def getVarRepos(String branch = 'master', String platform, String branding) {
  String reposOutput, scriptArgs

  checkoutRepos(getConstRepos(branch))

  scriptArgs = "--module \"${getModules(platform).join(' ')}\""
  if (platform != null) scriptArgs += " --platform \"${platform}\""
  if (branding != null) scriptArgs += " --branding \"${branding}\""

  if (platform.startsWith("win")) {
    reposOutput = powershell(
      script: "cd build_tools\\scripts\\develop; \
        python print_repositories.py ${scriptArgs}",
      returnStdout: true
    )
  } else {
    reposOutput = sh(
      script: "cd build_tools/scripts/develop && \
        ./print_repositories.py ${scriptArgs}",
      returnStdout: true
    )
  }

  ArrayList repos = []
  reposOutput.readLines().sort().each { line ->
    ArrayList lineSplit = line.split(" ")
    Map repo = [
      owner: "ONLYOFFICE",
      name: lineSplit[0],
      branch: "master",
      dir: (lineSplit[1] == null) ? "${lineSplit[0]}" : "${lineSplit[1]}/${lineSplit[0]}"
    ]
    if (branch != 'master') repo.branch = resolveScm(
        source: [
          $class: 'GitSCMSource',
          remote: "git@github.com:${repo.owner}/${repo.name}.git",
          traits: [[$class: 'jenkins.plugins.git.traits.BranchDiscoveryTrait']]
        ],
        targets: [branch, 'master']
      ).branches[0].name

    repos.add(repo)
  }

  return repos
}

void checkoutRepos(ArrayList repos) {
  echo repos.collect({"${it.owner}/${it.name} (${it.branch})"}).join("\n")
  repos.each {
    checkoutRepo(it.owner + "/" + it.name, it.branch, it.dir)
  }
}

void tagRepos(ArrayList repos, String tag) {
  repos.each {
    sh """
      cd ${it.dir}
      git tag -l | xargs git tag -d
      git fetch --tags
      git tag ${tag}
      git push origin --tags
    """
  }
}

// Configure

def getModules(String platform, String license = "any") {
  Boolean isOpenSource = license in ["opensource", "any"]
  Boolean isCommercial = license in ["commercial", "any"]
  Boolean pCore = platform in ["win_64", "win_32", "mac_64", "linux_64", "linux_arm64"]
  Boolean pDesktop = platform != "linux_arm64"
  Boolean pBuilder = platform in ["win_64", "linux_64", "linux_arm64"]
  Boolean pServer = platform in ["win_64", "linux_64", "linux_arm64"]
  Boolean pMobile = platform == "android"

  ArrayList modules = []
  if (params.core && isOpenSource && pCore)
    modules.add("core")
  if (params.desktop && isCommercial && pDesktop)
    modules.add("desktop")
  if (params.builder && isOpenSource && pBuilder)
    modules.add("builder")
  if ((((params.server_de || params.server_ee) && isCommercial) \
    || (params.server_ce && isOpenSource)) && pServer)
    modules.add("server")
  if (params.mobile && isOpenSource && pMobile)
    modules.add("mobile")

  return modules
}

def getConfigArgs(String platform = 'native', String license = 'opensource') {
  ArrayList modules = getModules(platform, license)
  if (platform.startsWith("win")) modules.add("tests")

  ArrayList args = []
  args.add("--module \"${modules.join(' ')}\"")
  args.add("--platform \"${platform}\"")
  args.add("--update false")
  args.add("--clean ${params.clean.toString()}")
  args.add("--qt-dir ${env.QT_PATH}")
  if (platform.endsWith("_xp"))
    args.add("--qt-dir-xp ${env.QT56_PATH}")
  if (license == "commercial")
    args.add("--branding \"onlyoffice\"")
  if (platform in ["win_64", "win_32"])
    args.add("--vs-version 2019")
  if (platform == "mac_64" && env.USE_V8 == "1")
    args.add("--config use_v8")
  if (platform == "android")
    args.add("--config release")
  if (params.beta)
    args.add("--beta 1")
  if (!params.extra_args.isEmpty())
    args.add(params.extra_args)

  return args.join(' ')
}

// Build

void build(String platform, String license = 'opensource') {
  Boolean isUnix = false

  if (platform.startsWith("win")) {

    bat "cd build_tools && \
      call python configure.py ${getConfigArgs(platform, license)} && \
      call python make.py"

  } else {

    sh "cd build_tools && \
      ./configure.py ${getConfigArgs(platform, license)} && \
      ./make.py"
    isUnix = true

  }

  if (license == "opensource" && platform in ["win_64", "win_32", "mac_64", "linux_64"]) {
    String os, arch, version, coreFile
    String branch = env.BRANCH_NAME

    if (platform.startsWith("win"))   os = "windows" else
    if (platform.startsWith("mac"))   os = "mac"     else
    if (platform.startsWith("linux")) os = "linux"

    if (platform in ["win_64", "win_32"])
      version = "${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}" else
    if (platform in ["linux_64", "mac_64"])
      version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"

    if (platform.endsWith("_32")) arch = "x86" else
    if (platform.endsWith("_64")) arch = "x64"

    coreFile = "core.7z"
    if (env.USE_UBUNTU20 == "1") coreFile = "core-ubuntu20.7z"

    Closure coreDeployPath = {
      return "${s3bucket}/${os}/core/${branch}/${it}/${arch}"
    }

    String cmdUpload = """
      aws s3 cp --acl public-read --no-progress \
        build_tools/out/${platform}/onlyoffice/core/core.7z \
        s3://${coreDeployPath(version)}/${coreFile}
      aws s3 sync --delete --acl public-read --no-progress \
        s3://${coreDeployPath(version)}/ \
        s3://${coreDeployPath('latest')}/
    """

    if (isUnix) sh cmdUpload else bat cmdUpload
  }
}

// Build Packages

void buildDesktop (String platform) {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"
  String product = "desktop"
  String buildPackage, fplatform, winDeployPath, macosDeployPath, scheme

  if (platform.startsWith("win")) {

    bat "cd desktop-apps && \
      make clean-package && \
      make packages"

    if      (platform.startsWith("win_64")) fplatform = "Windows x64"
    else if (platform.startsWith("win_32")) fplatform = "Windows x86"
    winDeployPath = "windows/${version}/desktop/"

    dir ("desktop-apps/win-linux/package/windows") {
      uploadFiles("*.exe", winDeployPath, product, fplatform, "Installer")
      uploadFiles("*.msi", winDeployPath, product, fplatform, "Installer")
      uploadFiles("*.zip", winDeployPath, product, fplatform, "Portable")
      uploadFiles("update/*.exe,update/*.xml,update/*.html",
        winDeployPath, product, fplatform, "WinSparkle")
    }

    dir ("desktop-apps/win-linux/package/windows") {
      uploadFiles("*.exe", winDeployPath, product, fplatform, "Installer")
      uploadFiles("*.msi", winDeployPath, product, fplatform, "Installer")
      uploadFiles("*.zip", winDeployPath, product, fplatform, "Portable")
      uploadFiles("update/*.exe,update/*.xml,update/*.html",
        winDeployPath, product, fplatform, "WinSparkle")
    }

  } else if (platform.startsWith("mac")) {

    if (platform == "mac_64" && env.USE_V8 == '1') {
      buildPackage = "diskimage-v8-x86_64"
      fplatform = "macOS x86-64 V8 (legacy)"
      scheme = "ONLYOFFICE-v8"
      macosDeployPath = "macos/${version}/v8/"
    } else if (platform == "mac_64") {
      buildPackage = "diskimage-x86_64"
      fplatform = "macOS x86-64"
      scheme = "ONLYOFFICE-x86_64"
      macosDeployPath = "macos/${version}/x86_64/"
    } else if (platform == "mac_arm64") {
      buildPackage = "diskimage-arm64"
      fplatform = "macOS ARM64"
      scheme = "ONLYOFFICE-arm"
      macosDeployPath = "macos/${version}/arm/"
    }

    sh "rm -rfv \
      ~/Library/Developer/Xcode/Archives/* \
      ~/Library/Caches/Sparkle_generate_appcast/*"
    sh "cd build_tools && \
      ./make_packages.py --product desktop --package ${buildPackage}"

    String appVersion = sh (
      script: "mdls -name kMDItemVersion -raw desktop-apps/macos/build/ONLYOFFICE.app",
      returnStdout: true).trim()

    dir ("desktop-apps/macos/build") {
      uploadFiles("*.dmg", macosDeployPath, product, fplatform, "Disk Image")
      uploadFiles("${scheme}-*.zip,update/*.delta,update/*.xml,update/*.html",
        macosDeployPath, product, fplatform, "Sparkle")
    }

  } else if (platform == "linux_64") {

    sh "cd desktop-apps/win-linux/package/linux && \
      make clean && \
      make packages"

    fplatform = "Linux x64"

    dir ("desktop-apps/win-linux/package/linux") {
      uploadFiles2(product, fplatform, [
        [section: "Ubuntu",   glob: "deb/*.deb",        dest: "ubuntu/"  ],
        [section: "CentOS",   glob: "rpm/**/*.rpm",     dest: "centos/"  ],
        [section: "AltLinux", glob: "apt-rpm/**/*.rpm", dest: "altlinux/"],
        [section: "Rosa",     glob: "urpmi/**/*.rpm",   dest: "rosa/"    ],
        [section: "Portable", glob: "tar/**/*.tar.gz",  dest: "linux/"   ]
        // [section: "AstraLinux Signed", glob: "deb-astra/*.deb",  dest: "astralinux/"]
      ])
    }

  }
}

void buildBuilder(String platform) {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"
  String product = "builder"
  String fplatform, winDeployPath

  if (platform.startsWith("win")) {

    bat "cd document-builder-package && \
      make clean && \
      make packages"

    if      (platform.startsWith("win_64")) fplatform = "Windows x64"
    else if (platform.startsWith("win_32")) fplatform = "Windows x86"
    winDeployPath = "windows/${version}/builder/"

    dir ("document-builder-package") {
      uploadFiles("exe/*.exe", winDeployPath, product, fplatform, "Installer")
      uploadFiles("zip/*.zip", winDeployPath, product, fplatform, "Portable")
    }

  } else if (platform == "linux_64") {

    sh "cd document-builder-package && \
      make clean && \
      make packages"

    fplatform = "Linux x64"

    dir ("document-builder-package") {
      uploadFiles2(product, fplatform, [
        [section: "Ubuntu",   glob: "deb/*.deb",    dest: "ubuntu/"],
        [section: "CentOS",   glob: "rpm/**/*.rpm", dest: "centos/"],
        [section: "Portable", glob: "tar/*.tar.gz", dest: "linux/" ]
      ])
    }

  }
}

void buildServer(String platform, String edition='community') {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"
  String product, productName, winDeployPath

  switch(edition) {
    case "community":
      product = "server_ce"
      productName = "DocumentServer"
      break
    case "enterprise":
      product = "server_ee"
      productName = "DocumentServer-EE"
      break
    case "developer":
      product = "server_de"
      productName = "DocumentServer-DE"
      break
  }

  if (platform == "win_64") {

    bat "cd document-server-package && \
      set \"PRODUCT_NAME=${productName}\" && \
      make clean && \
      make packages"

    fplatform = "Windows x64"
    winDeployPath = "windows/${version}/server/"

    dir ("document-server-package") {
      uploadFiles("exe/*.exe", winDeployPath, product, fplatform, "Installer")
    }

  } else if (platform == "linux_64") {

    sh "cd document-server-package && \
      export PRODUCT_NAME=${productName.toLowerCase()} && \
      make clean && \
      make packages"

    fplatform = "Linux x64"

    dir ("document-server-package") {
      uploadFiles2(product, fplatform, [
        [section: "Ubuntu",   glob: "deb/*.deb",        dest: "ubuntu/"  ],
        [section: "CentOS",   glob: "rpm/**/*.rpm",     dest: "centos/"  ],
        [section: "AltLinux", glob: "apt-rpm/**/*.rpm", dest: "altlinux/"],
        [section: "Portable", glob: "*.tar.gz",         dest: "linux/"   ]
      ])
    }

    sh "cd Docker-DocumentServer && \
      export PRODUCT_NAME=${productName.toLowerCase()} && \
      make clean && \
      make deploy"

  }
}

void buildAndroid(String branch = 'master', String config = 'release') {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"

  sh "cd build_tools/out && \
    zip -r ../../android-libs-${version}.zip ./android* ./js"
  uploadFiles("*.zip", "android/", "mobile", "Android", "Libs")
}

// Upload

void uploadFiles(String glob, String dest, String product, String platform, String section) {
  String s3uri, file
  Closure cmdUpload = { local, remote ->
    String cmd = "aws s3 cp --acl public-read --no-progress ${local} s3://${remote}"
    if (platform ==~ /^Windows.*/) bat cmd else sh cmd
  }
  Closure cmdMd5sum = {
    if (platform ==~ /^Windows.*/) {
      return powershell (
        script: "Get-FileHash ${it} -Algorithm MD5 | Select -ExpandProperty Hash",
        returnStdout: true).trim()
    } else if (platform ==~ /^macOS.*/) {
      return sh (script: "md5 -qs ${it}", returnStdout: true).trim()
    } else {
      return sh (script: "md5sum ${it} | cut -c -32", returnStdout: true).trim()
    }
  }

  findFiles(glob: glob).each {
    s3uri = "${s3deploy}/${dest}${dest.endsWith('/') ? it.name : ''}"
    file = dest.endsWith('/') ? it.name : dest.drop(dest.lastIndexOf('/')+1)
    cmdUpload(it.path, s3uri)

    listDeploy.add([
      product: product,
      platform: platform,
      section: section,
      path: s3uri,
      file: file,
      size: it.length,
      md5: cmdMd5sum(it.path)
      // sha256: cmdSha256sum(it.path)
    ])
  }
}

void uploadFiles2(String product, String platform, ArrayList items) {
  String s3uri, file
  Closure cmdUpload = { local, remote ->
    String cmd = "aws s3 cp --acl public-read --no-progress ${local} s3://${remote}"
    if (platform ==~ /^Windows.*/) bat cmd else sh cmd
  }
  Closure cmdMd5sum = {
    if (platform ==~ /^Windows.*/) {
      return powershell (
        script: "Get-FileHash ${it} -Algorithm MD5 | Select -ExpandProperty Hash",
        returnStdout: true).trim()
    } else if (platform ==~ /^macOS.*/) {
      return sh (script: "md5 -qs ${it}", returnStdout: true).trim()
    } else {
      return sh (script: "md5sum ${it} | cut -c -32", returnStdout: true).trim()
    }
  }

  items.each { item ->
    findFiles(glob: item.glob).each {
      s3uri = "${s3deploy}/${item.dest}${item.dest.endsWith('/') ? it.name : ''}"
      file = item.dest.endsWith('/') ? it.name : item.dest.drop(item.dest.lastIndexOf('/')+1)
      cmdUpload(it.path, s3uri)

      listDeploy.add([
        product: product,
        platform: platform,
        section: item.section,
        path: s3uri,
        file: file,
        size: it.length,
        md5: cmdMd5sum(it.path)
        // sha256: cmdSha256sum(it.path)
      ])
    }
  }
}

// Tests

void linuxTest() {
  checkoutRepo([owner: 'ONLYOFFICE', name: 'doc-builder-testing'], 'master')
  sh "docker rmi doc-builder-testing || true"
  sh "cd doc-builder-testing && \
    docker build --tag doc-builder-testing -f dockerfiles/debian-develop/Dockerfile . &&\
    docker run --rm doc-builder-testing bundle exec parallel_rspec spec -n 2"
}

// Reports

void generateReports() {
  Map deploy = listDeploy.groupBy { it.product }

  Boolean desktop = deploy.desktop != null
  Boolean builder = deploy.builder != null
  Boolean server_ce = deploy.server_ce != null
  Boolean server_ee = deploy.server_ee != null
  Boolean server_de = deploy.server_de != null
  Boolean mobile = deploy.mobile != null

  dir ("html") {
    sh """
      rm -fv *.html
      test -f style.css || wget -nv https://unpkg.com/style.css -O style.css
    """

    if (desktop)
      publishReport("DesktopEditors", ["desktop.html": deploy.desktop])
    if (builder)
      publishReport("DocumentBuilder", ["builder.html": deploy.builder])
    if (server_ce || server_ee || server_de) {
      Map serverReports = [:]
      if (server_ce) serverReports."server_ce.html" = deploy.server_ce
      if (server_ee) serverReports."server_ee.html" = deploy.server_ee
      if (server_de) serverReports."server_de.html" = deploy.server_de
      publishReport("DocumentServer", serverReports)
    }
    if (mobile)
      publishReport("Mobile", ["mobile.html": deploy.mobile])
  }
}

void publishReport(String title, Map files) {
  files.each {
    writeFile file: it.key, text: getHtml(it.value)
  }
  publishHTML([
    allowMissing: false,
    alwaysLinkToLastBuild: false,
    includes: files.collect({ it.key }).join(',') + ",*.css",
    keepAll: true,
    reportDir: '',
    reportFiles: files.collect({ it.key }).join(','),
    reportName: title,
    reportTitles: ''
  ])
}

def getHtml(ArrayList data) {
  String text, url
  Closure size = {
    return sh (script: "LANG=C numfmt --to=iec-i ${it}", returnStdout: true).trim()
  }

  text = "<html>\n<head>" \
    + "\n  <link rel=\"stylesheet\" href=\"style.css\">" \
    + "\n  <style type=\"text/css\">body { margin: 24px; }</style>" \
    + "\n<head>\n<body>"
  data.groupBy { it.platform }.sort().each { platform, sections ->
    text += "\n  <h3>${platform}</h3>\n  <ul>"
    sections.groupBy { it.section }.each { section, files ->
      text += "\n    <li><b>${section}</b></li>\n    <ul>"
      files.each {
        url = "https://s3.${s3region}.amazonaws.com/${it.path}"
        text += "\n      <li>" \
          + "\n        <a href=\"${url}\">${it.file}</a>" \
          + ", Size: ${size(it.size)}B" \
          + ", MD5: <code>${it.md5}</code>" \
          + "\n      </li>"
      }
      text += "\n    </ul>"
    }
    text += "\n  </ul>"
  }
  text += "\n</body>\n</html>"

  return text
}

// Notifications

def getJobStats(String jobStatus) {
  String text = "Build [${currentBuild.fullDisplayName}]" \
    + "(${currentBuild.absoluteUrl}) ${jobStatus}"
  stageStats.each { stage, status ->
    text += "\n${status ? '🔵' : '🔴'} ${stage}"
  }
  return text
}

void sendTelegramMessage(String text, String chatId, Boolean markdown = true) {
  if (params.notify) sh(
    label: "Send Telegram Message",
    script: "curl -X POST -s -S \
      -d chat_id=${chatId} \
      ${markdown ? '-d parse_mode=markdown' : ''} \
      -d disable_web_page_preview=true \
      --data-urlencode text='${text}' \
      https://api.telegram.org/bot\$TELEGRAM_TOKEN/sendMessage"
    )
}
