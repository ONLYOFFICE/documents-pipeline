defaults = [
  channel:          'other',
  version:          '99.99.99',
  clean:            true,
  windows_x64:      true,
  windows_x86:      true,
  windows_x64_xp:   true,
  windows_x86_xp:   true,
  darwin_x86_64:    true,
  darwin_arm64:     true,
  darwin_x86_64_v8: true,
  linux_x86_64:     true,
  linux_aarch64:    true,
  linux_x86_64_cef: true,
  android:          true,
  core:             true,
  desktop:          true,
  builder:          true,
  server_ce:        true,
  server_ee:        true,
  server_de:        true,
  mobile:           true,
  password:         false,
  beta:             false,
  sign:             true,
  schedule:         'H 17 * * *',
  repo_owner:       'ONLYOFFICE',
  repo_name:        'onlyoffice',
]

if (env.BRANCH_NAME == 'develop') {
  defaults.putAll([
    channel:          'nightly',
    darwin_x86_64_v8: false,
    linux_x86_64_cef: false,
    android:          false,
    server_ce:        false,
    server_de:        false,
    mobile:           false,
    beta:             true,
  ])
}
if (env.BRANCH_NAME ==~ /^(hotfix|release)\/.+/) {
  defaults.putAll([
    channel:          'test',
    version:          env.BRANCH_NAME.replaceAll(/.+\/v(?=[0-9.]+)/,''),
    schedule:         'H 23 * * *',
  ])
}

pipeline {
  agent none
  environment {
    COMPANY_NAME = 'ONLYOFFICE'
    BUILD_CHANNEL = "${defaults.channel}"
    PRODUCT_VERSION = "${defaults.version}"
    TELEGRAM_TOKEN = credentials('telegram-bot-token')
    S3_BUCKET = 'repo-doc-onlyoffice-com'
    S3_BASE_URL = 'https://s3.eu-west-1.amazonaws.com/repo-doc-onlyoffice-com'
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
      description:  'Build Windows x64 XP targets (Visual Studio 2015)',
      defaultValue: defaults.windows_x64_xp
    )
    booleanParam (
      name:         'windows_x86_xp',
      description:  'Build Windows x86 XP targets (Visual Studio 2015)',
      defaultValue: defaults.windows_x86_xp
    )
    // macOS
    booleanParam (
      name:         'darwin_x86_64',
      description:  'Build macOS x86-64 targets',
      defaultValue: defaults.darwin_x86_64
    )
    booleanParam (
      name:         'darwin_arm64',
      description:  'Build macOS arm64 targets',
      defaultValue: defaults.darwin_arm64
    )
    booleanParam (
      name:         'darwin_x86_64_v8',
      description:  'Build macOS x86-64 V8 targets',
      defaultValue: defaults.darwin_x86_64_v8
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
    booleanParam (
      name:         'linux_x86_64_cef',
      description:  'Build Linux x86-64 cef107 targets',
      defaultValue: defaults.linux_x86_64_cef
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
    /*
    booleanParam (
      name:         'password_protection',
      description:  'Enable password protection',
      defaultValue: defaults.password
    )
    */
    booleanParam (
      name:         'beta',
      description:  'Beta (enabled anyway on develop)',
      defaultValue: defaults.beta
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
          branchDir = env.BRANCH_NAME.replaceAll(/\//,'_')
          gitTag = "v${env.PRODUCT_VERSION}.${env.BUILD_NUMBER}"
          deployData = []
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
            start('windows_x64')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
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
          environment {
            UNAME_M = 'i686'
          }
          steps {
            start('windows_x86')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        stage('Windows x64 XP') {
          agent {
            node {
              label 'windows_x64_xp'
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
            start('windows_x64_xp')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        stage('Windows x86 XP') {
          agent {
            node {
              label 'windows_x86_xp'
              customWorkspace "C:\\oo\\${branchDir}_x86_xp"
            }
          }
          when {
            expression { params.windows_x86_xp }
            beforeAgent true
          }
          environment {
            _WIN_XP = '1'
            UNAME_M = 'i686'
          }
          steps {
            start('windows_x86_xp')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        // macOS
        stage('macOS x86_64') {
          agent { label 'darwin_x86_64' }
          when {
            expression { params.darwin_x86_64 }
            beforeAgent true
          }
          environment {
            FASTLANE_HIDE_TIMESTAMP = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          steps {
            start('darwin_x86_64')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        stage('macOS arm64') {
          agent { label 'darwin_arm64' }
          when {
            expression { params.darwin_arm64 }
            beforeAgent true
          }
          environment {
            FASTLANE_HIDE_TIMESTAMP = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          steps {
            start('darwin_arm64')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        stage('macOS x86_64 V8') {
          agent { label 'darwin_x86_64_v8' }
          when {
            expression { params.darwin_x86_64_v8 }
            beforeAgent true
          }
          environment {
            FASTLANE_HIDE_TIMESTAMP = '1'
            FASTLANE_SKIP_UPDATE_CHECK = '1'
            APPLE_ID = credentials('macos-apple-id')
            TEAM_ID = credentials('macos-team-id')
            FASTLANE_APPLE_APPLICATION_SPECIFIC_PASSWORD = credentials('macos-apple-password')
            CODESIGNING_IDENTITY = 'Developer ID Application'
          }
          steps {
            start('darwin_x86_64_v8')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        // Linux
        stage('Linux x86_64') {
          agent { label 'linux_x86_64' }
          when {
            expression { params.linux_x86_64 }
            beforeAgent true
          }
          environment {
            GITHUB_TOKEN = credentials('github-token')
            // TAR_RELEASE_SUFFIX = '-gcc5'
            // DEB_RELEASE_SUFFIX = '~stretch'
            RPM_RELEASE_SUFFIX = '.el7'
            SUSE_RPM_RELEASE_SUFFIX = '.suse12'
          }
          steps {
            start('linux_x86_64')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        stage('Linux aarch64') {
          agent { label 'linux_aarch64' }
          when {
            expression { params.linux_aarch64 }
            beforeAgent true
          }
          environment {
            // TAR_RELEASE_SUFFIX = '-gcc5'
            // DEB_RELEASE_SUFFIX = '~stretch'
            RPM_RELEASE_SUFFIX = '.el7'
            SUSE_RPM_RELEASE_SUFFIX = '.suse12'
          }
          steps {
            start('linux_aarch64')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        stage('Linux x86_64 CEF 107') {
          agent { label 'linux_x86_64_cef' }
          when {
            expression { params.linux_x86_64_cef }
            beforeAgent true
          }
          environment {
            GITHUB_TOKEN = credentials('github-token')
            TAR_RELEASE_SUFFIX = '-cef107'
            DEB_RELEASE_SUFFIX = '~cef107'
            RPM_RELEASE_SUFFIX = '~cef107.el7'
            SUSE_RPM_RELEASE_SUFFIX = '~cef107.suse12'
          }
          steps {
            start('linux_x86_64_cef')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
        // Android
        stage('Android') {
          agent { label 'android' }
          when {
            expression { params.android && params.mobile }
            beforeAgent true
          }
          steps {
            start('android')
          }
          post {
            success  { setStageStats(0) }
            unstable { setStageStats(1) }
            failure  { setStageStats(2) }
          }
        }
      }
    }
    stage('Docker') {
      agent { label 'linux_x86_64 || linux_aarch64' }
      environment {
        GITHUB_TOKEN = credentials('github-token')
      }
      when {
        expression {
          (params.linux_x86_64 || params.linux_aarch64) && (params.server_ce || params.server_ee || params.server_de)
        }
        beforeAgent true
      }
      steps {
        script {
          catchError(
            buildResult: 'UNSTABLE',
            stageResult: 'FAILURE',
            message: 'Docker build failure'
          ) {
            checkDocker()
          }
        }
      }
      post {
        success  { setStageStats(0) }
        unstable { setStageStats(1) }
        failure  { setStageStats(2) }
      }
    }
  }
  post {
    always {
      node('built-in') {
        script {
          checkout scm
          buildAppcast()
          generateReports()
        }
      }
    }
    fixed {
      node('built-in') { script { sendTelegramMessage('fixed') } }
    }
    unstable {
      node('built-in') { script { sendTelegramMessage('unstable') } }
    }
    failure {
      node('built-in') { script { sendTelegramMessage('failure') } }
    }
  }
}

// Stages

void start(String platform) {
  echo 'PLATFORM=' + platform + '\nNODE_NAME=' + env.NODE_NAME

  if (params.wipe)
    deleteDir()
  else if (params.clean && params.desktop && platform != 'android')
    dir ('desktop-apps') { deleteDir() }

  if (platform.startsWith('windows')) startWindows(platform)
  if (platform.startsWith('darwin'))  startDarwin(platform)
  if (platform.startsWith('linux'))   startLinux(platform)
  if (platform == 'android')          startAndroid(platform)
}

void startWindows(String platform) {
  ArrayList varRepos = getVarRepos(platform, defaults.repo_name)
  checkoutRepos(varRepos)

  buildArtifacts(platform, 'opensource')
  buildPackages(platform, 'opensource')

  buildArtifacts(platform, 'commercial')
  buildPackages(platform, 'commercial')
}

void startDarwin(String platform) {
  ArrayList varRepos = getVarRepos(platform, defaults.repo_name)
  checkoutRepos(varRepos)

  buildArtifacts(platform, 'opensource')
  buildPackages(platform, 'opensource')

  if (env.BRANCH_NAME ==~ /^(hotfix|release)\/.+/) {
    buildArtifacts(platform, 'commercial')
    buildPackages(platform, 'commercial')
  }
}

void startLinux(String platform) {
  ArrayList constRepos = getConstRepos()
  ArrayList varRepos = getVarRepos(platform, defaults.repo_name)
  ArrayList allRepos = constRepos.plus(varRepos)
  checkoutRepos(varRepos)

  buildArtifacts(platform, 'opensource')
  buildPackages(platform, 'opensource')

  buildArtifacts(platform, 'commercial')
  buildPackages(platform, 'commercial')

  if ((platform == 'linux_x86_64') && (params.server_ce || params.server_ee || params.server_de)) {
    if (env.COMPANY_NAME == 'ONLYOFFICE') {
      buildDocker()
      tagRepos(allRepos)
    } else {
      ArrayList buildDockerServer = []
      if (params.server_ee) buildDockerServer.add('-ee')
      if (params.server_de) buildDockerServer.add('-de')
      buildDockerServer.each {
        sh label: 'DOCKER DOCUMENTSERVER' + it.toUpperCase(), script: """
          cd Docker-DocumentServer
          make clean
          make deploy -e PRODUCT_EDITION=${it} -e ONLYOFFICE_VALUE=ds \
            -e PACKAGE_VERSION=\$PRODUCT_VERSION-\$BUILD_NUMBER \
            -e PACKAGE_BASEURL=\$S3_BASE_URL/server/linux/debian
        """
      }
    }
  }
}

void startAndroid(String platform = 'android') {
  ArrayList varRepos = getVarRepos(platform)
  checkoutRepos(varRepos)

  buildArtifacts(platform, 'opensource')
  buildPackages(platform, 'opensource')
}

// Build

void buildArtifacts(String platform, String license = 'opensource') {
  ArrayList modules = getModuleList(platform, license)
  if (!modules) return

  ArrayList args = []
  args.add("--module \"${modules.join(' ')}\"")
  args.add("--platform ${getPrefix(platform)}")
  args.add("--update false")
  args.add("--clean ${params.clean.toString()}")
  args.add("--qt-dir ${env.QT_PATH}")
  if (platform in ["windows_x64_xp", "windows_x86_xp"])
    args.add("--qt-dir-xp ${env.QT56_PATH}")
  if (license == "commercial")
    args.add("--branding ${defaults.repo_name}")
  if (platform in ["windows_x64", "windows_x86"])
    args.add("--vs-version 2019")
  if (platform == "darwin_x86_64_v8")
    args.add("--config use_v8")
  if (platform == "linux_x86_64_cef")
    args.add("--config cef_version_107")
  if (platform == "android")
    args.add("--config release")
  // if (params.password_protection)
  //   args.add("--features \"enable_protection disable_signatures\"")
  if (params.beta)
    args.add("--beta 1")
  if (!params.extra_args.isEmpty())
    args.add(params.extra_args)

  String label = "artifacts ${license}".toUpperCase()
  if (!platform.startsWith('windows')) {
    sh label: label, script: """
      cd build_tools
      ./configure.py ${args.join(' ')}
      ./make.py
    """
  } else {
    powershell label: label, script: """
      cd build_tools
      python configure.py ${args.join(' ')}
      python make.py
    """
  }
}

void buildPackages(String platform, String license = 'opensource') {
  ArrayList targets = getTargetList(platform, license)
  if (!targets) return
  targets.addAll(['clean', 'deploy'])
  if (params.signing && platform.startsWith('windows'))
    targets.add('sign')

  ArrayList args = [
    "--platform ${platform}",
    "--targets ${targets.join(' ')}",
    "--version ${env.PRODUCT_VERSION}",
    "--build ${env.BUILD_NUMBER}",
  ]
  if (env.COMPANY_NAME != 'ONLYOFFICE')
    args.add("--branding ${defaults.repo_name}")

  String label = "packages ${license}".toUpperCase()
  if (!platform.startsWith('windows'))
    sh label: label, script: """
      cd build_tools
      ./make_package.py ${args.join(' ')}
    """
  else
    powershell label: label, script: """
      cd build_tools
      python make_package.py ${args.join(' ')}
    """

  if (fileExists('deploy.json')) deployData += readJSON(file: 'deploy.json')
}

ArrayList getModuleList(String platform, String license = 'any') {
  def p = params
  LinkedHashMap l = [
    os: license in ['opensource', 'any'],
    com: license in ['commercial', 'any'],
  ]
  LinkedHashMap map = [
    windows_x64: [
      core: p.core && l.os,
      desktop: p.desktop && l.com,
      builder: p.builder && l.os,
      server: (p.server_ce && l.os) || ((p.server_de || p.server_ee) && l.com),
    ],
    windows_x86: [
      core: p.core && l.os,
      desktop: p.desktop && l.com,
      builder: p.builder && l.os,
    ],
    windows_x64_xp: [
      desktop: p.desktop && l.com,
    ],
    windows_x86_xp: [
      desktop: p.desktop && l.com,
    ],
    darwin_x86_64: [
      core: p.core && l.os,
      desktop: p.desktop && l.com,
    ],
    darwin_arm64: [
      core: p.core && l.os,
      desktop: p.desktop && l.com,
    ],
    darwin_x86_64_v8: [
      desktop: p.desktop && l.com,
    ],
    linux_x86_64: [
      core: p.core && l.os,
      desktop: p.desktop && l.com,
      builder: p.builder && l.os,
      server: (p.server_ce && l.os) || ((p.server_de || p.server_ee) && l.com),
    ],
    linux_aarch64: [
      builder: p.builder && l.os,
      server: (p.server_ce && l.os) || ((p.server_de || p.server_ee) && l.com),
    ],
    linux_x86_64_cef: [
      desktop: p.desktop && l.com,
    ],
    android: [
      mobile: p.mobile && l.os,
    ],
  ]
  ArrayList modules = []

  map[platform].each {
    if (it.value) modules.add(it.key)
  }

  return modules
}

ArrayList getTargetList(String platform, String license = 'any') {
  def p = params
  LinkedHashMap l = [
    os: license in ['opensource', 'any'],
    com: license in ['commercial', 'any'],
  ]
  LinkedHashMap map = [
    windows_x64: [
      'core': p.core && l.os,
      'desktop': p.desktop && l.com,
      'builder': p.builder && l.os,
      'server-community': p.server_ce && l.os,
      'server-developer': p.server_de && l.com,
      'server-enterprise': p.server_ee && l.com,
    ],
    windows_x86: [
      'core': p.core && l.os,
      'desktop': p.desktop && l.com,
      'builder': p.builder && l.os,
    ],
    windows_x64_xp: [
      'desktop': p.desktop && l.com,
    ],
    windows_x86_xp: [
      'desktop': p.desktop && l.com,
    ],
    darwin_x86_64: [
      'core': p.core && l.os,
      'desktop': p.desktop && l.com,
    ],
    darwin_arm64: [
      'core': p.core && l.os,
      'desktop': p.desktop && l.com,
    ],
    darwin_x86_64_v8: [
      'desktop': p.desktop && l.com,
    ],
    linux_x86_64: [
      'core': p.core && l.os,
      'closure-maps-os': l.os,
      'closure-maps-com': l.com,
      'desktop': p.desktop && l.com,
      'builder': p.builder && l.os,
      'server-community': p.server_ce && l.os,
      'server-developer': p.server_de && l.com,
      'server-enterprise': p.server_ee && l.com,
    ],
    linux_aarch64: [
      'builder': p.builder && l.os,
      'server-community': p.server_ce && l.os,
      'server-developer': p.server_de && l.com,
      'server-enterprise': p.server_ee && l.com,
    ],
    linux_x86_64_cef: [
      'desktop': p.desktop && l.com,
    ],
    android: [
      'mobile': p.mobile && l.os,
    ],
  ]
  ArrayList targets = []

  map[platform].each {
    if (it.value) targets.add(it.key)
  }

  return targets
}

String getPrefix(String platform) {
  return [
    windows_x64:      'win_64',
    windows_x86:      'win_32',
    windows_x64_xp:   'win_64_xp',
    windows_x86_xp:   'win_32_xp',
    darwin_x86_64:    'mac_64',
    darwin_arm64:     'mac_arm64',
    darwin_x86_64_v8: 'mac_64',
    linux_x86_64:     'linux_64',
    linux_aarch64:    'linux_arm64',
    linux_x86_64_cef: 'linux_64',
    android:          'android',
  ][platform]
}

// Docker

void buildDocker() {
  sh label: 'DOCKER RUN', script: """
    gh workflow run 4testing-build.yml \
      --repo ONLYOFFICE/Docker-DocumentServer \
      --ref \$BRANCH_NAME \
      -f build=\$BUILD_NUMBER \
      -f amd64=${params.linux_x86_64} \
      -f arm64=${params.linux_aarch64} \
      -f community=${params.server_ce} \
      -f enterprise=${params.server_ee} \
      -f developer=${params.server_de}
  """
}

void checkDocker() {
  sh label: 'DOCKER CHECK', script: """
    REPO=ONLYOFFICE/Docker-DocumentServer
    sleep 5
    RUN_ID=\$(gh run list --repo \$REPO --workflow 4testing-build.yml \
      --branch \$BRANCH_NAME --json databaseId --jq '.[0].databaseId')
    gh --repo \$REPO run watch \$RUN_ID --interval 15 > /dev/null
    gh --repo \$REPO run view \$RUN_ID --verbose --exit-status
  """
}

// Repos

def getConstRepos(String branch = env.BRANCH_NAME) {
  return [
    [owner: 'ONLYOFFICE',        name: 'build_tools'],
    [owner: defaults.repo_owner, name: defaults.repo_name]
  ].each {
    it.branch = branch
  }
}

def getVarRepos(String platform, String branding = '', String branch = env.BRANCH_NAME) {
  checkoutRepos(getConstRepos())

  ArrayList modules = getModuleList(platform)
  ArrayList args = []
  if (modules)             args.add("--module \"${modules.join(' ')}\"")
  if (!platform.isEmpty()) args.add("--platform ${getPrefix(platform)}")
  if (!branding.isEmpty()) args.add("--branding ${branding}")

  String reposOutput
  if (!platform.startsWith('windows')) {
    reposOutput = sh label: "REPOS PRINT", returnStdout: true, script: """
      cd build_tools/scripts/develop
      ./print_repositories.py ${args.join(' ')}
    """
  } else {
    reposOutput = powershell label: "REPOS PRINT", returnStdout: true, script: """
      cd build_tools\\scripts\\develop
      python print_repositories.py ${args.join(' ')}
    """
  }

  ArrayList repos = []
  reposOutput.readLines().sort().each { line ->
    Map repo = [
      owner: 'ONLYOFFICE',
      name: line,
      branch: 'master'
    ]
    if (branch != 'master') {
      int retryCount = 0
      retry(3) {
        if (retryCount > 0) sleep(60)
        retryCount++
        repo.branch = resolveScm(
          source: [
            $class: 'GitSCMSource',
            remote: "git@github.com:${repo.owner}/${repo.name}.git",
            traits: [[$class: 'jenkins.plugins.git.traits.BranchDiscoveryTrait']]
          ],
          targets: [branch, 'master']
        ).branches[0].name
      }
    }

    repos.add(repo)
  }

  return repos
}

void checkoutRepo(String repo, String branch = 'master') {
  int retryCount = 0
  retry(3) {
    if (retryCount > 0) sleep(30)
    retryCount++
    checkout([
      $class: 'GitSCM',
      branches: [[name: 'refs/heads/' + branch]],
      doGenerateSubmoduleConfigurations: false,
      extensions: [
        [$class: 'AuthorInChangelog'],
        [$class: 'RelativeTargetDirectory', relativeTargetDir: repo.minus(~/^.+\//)],
        [$class: 'ScmName', name: repo],
        [$class: 'SubmoduleOption', recursiveSubmodules: true]
      ],
      submoduleCfg: [],
      userRemoteConfigs: [[url: "git@github.com:${repo}.git"]]
    ])
  }
}

void checkoutRepos(ArrayList repos) {
  echo repos.collect({"${it.owner}/${it.name} (${it.branch})"}).join('\n')
  repos.each {
    checkoutRepo(it.owner + '/' + it.name, it.branch)
  }
}

void tagRepos(ArrayList repos, String tag = gitTag) {
  repos.each {
    if (it.name != 'onlyoffice.github.io')
      sh label: "REPO TAG: ${it.name}", script: """
        cd ${it.name}
        git tag -l | xargs git tag -d
        git fetch --tags
        git tag ${tag}
        git push origin --tags
      """
  }
}

// Post Actions

void buildAppcast() {
  if (!(params.desktop && (params.windows_x64 || params.windows_x86))) return
  withCredentials([
    string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
  ]) {
    sh label: 'APPCAST', returnStatus: true,
      script: './appcast.sh'
  }
  if (fileExists('deploy.json')) deployData += readJSON(file: 'deploy.json')
}

void generateReports() {
  Map deploy = deployData.groupBy { it.product }
  println deploy

  Boolean core = deploy.core != null
  Boolean desktop = deploy.desktop != null
  Boolean builder = deploy.builder != null
  Boolean server_ce = deploy.server_community != null
  Boolean server_ee = deploy.server_enterprise != null
  Boolean server_de = deploy.server_developer != null
  Boolean mobile = deploy.mobile != null

  deleteDir()
  if (core)
    publishReport('Core', ['core.html': deploy.core])
  if (desktop)
    publishReport('DesktopEditors', ['desktop.html': deploy.desktop])
  if (builder)
    publishReport('DocumentBuilder', ['builder.html': deploy.builder])
  if (server_ce || server_ee || server_de) {
    Map serverReports = [:]
    if (server_ce) serverReports.'server_community.html' = deploy.server_community
    if (server_ee) serverReports.'server_enterprise.html' = deploy.server_enterprise
    if (server_de) serverReports.'server_developer.html' = deploy.server_developer
    publishReport('DocumentServer', serverReports)
  }
  if (mobile)
    publishReport('Mobile', ['mobile.html': deploy.mobile])
  writeJSON file: 'deploy.json', json: deployData
  archiveArtifacts '*.html, *.json'

  currentBuild.description = ''
  Map links = [:]
  if (core)      links['Core'] = 'core.html'
  if (desktop)   links['DesktopEditors'] = 'desktop.html'
  if (builder)   links['DocumentBuilder'] = 'builder.html'
  if (server_ce) links['DocumentServer CE'] = 'server_community.html'
  if (server_de) links['DocumentServer DE'] = 'server_developer.html'
  if (server_ee) links['DocumentServer EE'] = 'server_enterprise.html'
  if (mobile)    links['Mobile'] = 'mobile.html'
  links.each {
    if (!currentBuild.description.isEmpty()) currentBuild.description += '<br>'
    currentBuild.description += "<a href=\"${env.S3_BASE_URL}/reports/${env.BRANCH_NAME}/${env.BUILD_NUMBER}/${it.value}\" target=\"_blank\">${it.key}</a>"
  }
}

void publishReport(String title, Map files) {
  files.each {
    writeFile file: it.key, text: getHtml(title, it.value)
    withCredentials([
      string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
      string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
    ]) {
      sh label: 'REPORTS UPLOAD', returnStatus: true, script: """
        aws s3 cp --no-progress --acl public-read \
          ${it.key} s3://\$S3_BUCKET/reports/\$BRANCH_NAME/\$BUILD_NUMBER/
        echo "\$S3_BASE_URL/reports/\$BRANCH_NAME/\$BUILD_NUMBER/${it.key}"
        aws s3 cp --no-progress --acl public-read \
          ${it.key} s3://\$S3_BUCKET/reports/\$BRANCH_NAME/latest/
        echo "\$S3_BASE_URL/reports/\$BRANCH_NAME/latest/${it.key}"
      """
    }
  }
}

def getHtml(String product, ArrayList data) {
  String text, url
  String now = new Date().format('yyyy-MM-dd HH:mm', TimeZone.getTimeZone('Europe/Moscow'))
  Closure size = {
    return sh (script: "LANG=C numfmt --to=iec-i ${it}", returnStdout: true).trim()
  }

  text = "<html>\n<head>" \
    + "\n<title>${env.COMPANY_NAME} ${product} - ${env.BRANCH_NAME} - ${env.BUILD_NUMBER}</title>" \
    + '\n<link rel="shortcut icon" sizes="16x16" href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAq1BMVEUAAABgv9+Pv0Cfv0D/cEBgv+f/cEBdwuv/bT1cv+f/cDz/bj18v3yprzleweqDwXOUwTiWwTj/bjtav+dtv61wv62Svzi3pTiVwTevrTr/bjxev+eTvziVvzj/cD5dweiWwTldwOmVwDj/bz3/bz1cwOiUwDiVwDj/bz1cwOiVwDldwOhewOiVwDiWwDj/bz3/bz5dwOlewOmUwDeVwDf/bz1dwOiVwDj/bz375yNXAAAANnRSTlMAEBAQECAgPz9AQE9QUF9fX19fYGBgYGBvf3+AgICAj4+fn5+vv7+/v8/P39/f39/f7+/v7++96DlEAAAAj0lEQVR42lXJA5ZEQRBE0Wh73Pi2zar9r2xKg75H+SKhHA74Z2H081y+LP4nV3oblhtHpcCmYn6SAZ/RX0Y7AKvH1VP59b4CrJEQ/+x1nfOaEFKbuFWEiR83loQMD4BPSv62ZH0MPzTxjS92cARSSmkT3F3/Yk/sTLENKBeyBxPsATUxk76GtNVblX9Oe5XfxIMfXH9c3hQAAAAASUVORK5CYII=" type="image/png">' \
    + "\n<link rel=\"stylesheet\" href=\"https://unpkg.com/@primer/css@20.8.3/dist/primer.css\">" \
    + "\n</head>\n<body><div class=\"container-lg px-3 my-5 markdown-body\">" \
    + "\n<h1>${env.COMPANY_NAME} ${product} - ${env.BRANCH_NAME} - ${env.BUILD_NUMBER}</h1>" \
    + "\n<p class =\"color-fg-muted\">${now} MSK</p>"
  data.groupBy { it.platform }.sort().each { platform, types ->
    text += "\n<h2>${platform}</h2>\n<dl>"
    types.groupBy { it.type }.each { type, files ->
      text += "\n<dt>${type}</dt>\n<dd>"
      files.each {
        title = it.key.minus(~/^.+\//)
        url = "${env.S3_BASE_URL}/${it.key}"
        text += "\n<a href=\"${url}\">${title}</a> (${size(it.size)}B)<br>"
      }
      text += "\n</dd>"
    }
    text += "\n</dl>"
  }
  text += "\n</div>\n</body>\n</html>"

  return text
}

void setStageStats(int status, String stageName = env.STAGE_NAME) {
  stageStats[stageName] = status
}

void sendTelegramMessage(String jobStatus, String chatId = '-1001773122025') {
  if (!params.notify) return
  String text = 'Build [' + currentBuild.fullDisplayName \
      + '](' + currentBuild.absoluteUrl + ') ' + jobStatus
  ArrayList icons = ['🟢', '🟡', '🔴']
  stageStats.sort().each { stage, code ->
    text += '\n' + icons[code] + ' ' + stage.replaceAll('_','\\\\_')
  }

  sh label: 'TELEGRAM MESSAGE SEND', script: """
    curl -X POST -s -S \
      -d chat_id=${chatId} \
      -d parse_mode=markdown \
      -d disable_web_page_preview=true \
      --data-urlencode text='${text}' \
      https://api.telegram.org/bot\$TELEGRAM_TOKEN/sendMessage
  """
}
