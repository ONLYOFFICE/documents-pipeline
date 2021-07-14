// Repos

listRepos = [
  [name: 'build_tools'],
  [name: 'core'],
  [name: 'core-fonts'],
  [name: 'desktop-apps'],
  [name: 'desktop-sdk'],
  [name: 'dictionaries'],
  [name: 'document-builder-package'],
  [name: 'document-server-integration'],
  [name: 'document-server-package'],
  [name: 'document-templates'],
  [name: 'documents-pipeline'],
  [name: 'onlyoffice'],
  [name: 'plugin-autocomplete',  dir: 'sdkjs-plugins/plugin-autocomplete'],
  [name: 'plugin-easybib',       dir: 'sdkjs-plugins/plugin-easybib'],
  [name: 'plugin-highlightcode', dir: 'sdkjs-plugins/plugin-highlightcode'],
  [name: 'plugin-macros',        dir: 'sdkjs-plugins/plugin-macros'],
  [name: 'plugin-mendeley',      dir: 'sdkjs-plugins/plugin-mendeley'],
  [name: 'plugin-ocr',           dir: 'sdkjs-plugins/plugin-ocr'],
  [name: 'plugin-photoeditor',   dir: 'sdkjs-plugins/plugin-photoeditor'],
  [name: 'plugin-speech',        dir: 'sdkjs-plugins/plugin-speech'],
  [name: 'plugin-thesaurus',     dir: 'sdkjs-plugins/plugin-thesaurus'],
  [name: 'plugin-translator',    dir: 'sdkjs-plugins/plugin-translator'],
  [name: 'plugin-wordpress',     dir: 'sdkjs-plugins/plugin-wordpress'],
  [name: 'plugin-youtube',       dir: 'sdkjs-plugins/plugin-youtube'],
  [name: 'plugin-zotero',        dir: 'sdkjs-plugins/plugin-zotero'],
  [name: 'sdkjs'],
  [name: 'sdkjs-comparison'],
  [name: 'sdkjs-content-controls'],
  [name: 'sdkjs-disable-features'],
  [name: 'sdkjs-sheet-views'],
  [name: 'server'],
  [name: 'server-license'],
  [name: 'server-lockstorage'],
  [name: 'web-apps'],
  [name: 'web-apps-mobile'],
  [name: 'Docker-DocumentServer'],
  [name: 'DocumentBuilder']
].each {
  if (it.owner == null) it.owner = 'ONLYOFFICE'
}

return this

void checkoutRepo(Map repo, String branch = 'master') {
  if (repo.dir == null) repo.dir = repo.name
  checkout([
    $class: 'GitSCM',
    branches: [[name: branch]],
    doGenerateSubmoduleConfigurations: false,
    extensions: [
      [$class: 'SubmoduleOption', recursiveSubmodules: true],
      [$class: 'RelativeTargetDirectory', relativeTargetDir: repo.dir],
      [$class: 'ScmName', name: "${repo.owner}/${repo.name}"]
    ],
    submoduleCfg: [],
    userRemoteConfigs: [[url: "git@github.com:${repo.owner}/${repo.name}.git"]]
  ])
}

void checkoutRepos(String branch = 'master') {
  for (repo in listRepos) {
    checkoutRepo(repo, branch)
  }
}

void tagRepos(String tag) {
  for (repo in listRepos) {
    sh "cd ${repo.dir} && \
      git tag -l | xargs git tag -d && \
      git fetch --tags && \
      git tag ${tag} && \
      git push origin --tags"
  }
}

// Configure

def getConfigArgs(String platform = 'native', String license = 'opensource') {
  Boolean core = false
  Boolean editors = false
  Boolean builder = false
  Boolean server = false
  Boolean branding = false

  switch(license) {
    case "opensource":
      core = params.core
      builder = params.builder
      server = params.server_ce
      break
    case "freemium":
      editors = params.editors
      branding = true
      break
    case "commercial":
      server = params.server_ee || params.server_de
      branding = true
      break
  }

  Boolean isWin = platform.startsWith("win")
  Boolean isWinXP = isWin && platform.endsWith("_xp")
  Boolean isMacOS = platform.startsWith("mac")
  Boolean isMacOS86 = isMacOS && env._X86 == "1"

  ArrayList modules = []
  if (core)                modules.add("core")
  if (editors)             modules.add("desktop")
  if (builder && !isMacOS) modules.add("builder")
  if (server && !isMacOS)  modules.add("server")
  if (isWin)               modules.add("tests")

  ArrayList args = []
  args.add("--module \"${modules.join(' ')}\"")
  args.add("--platform \"${platform}\"")
  args.add("--update false")
  args.add("--clean ${params.clean.toString()}")
  args.add("--qt-dir \"${env.QT_PATH}\"")
  if (isWinXP) args.add("--qt-dir-xp \"${env.QT56_PATH}\"")
  if (branding) args.add("--branding \"onlyoffice\"")
  if (isMacOS) args.add("--compiler \"clang\"")
  if (isMacOS86) args.add("--config \"use_v8\"")
  if (params.beta) args.add("--beta 1")
  if (!params.extra_args.isEmpty()) args.add(params.extra_args)

  return args.join(' ')
}

// Build

void build(String platform, String license = 'opensource') {
  Boolean isUnix = false

  if (platform.startsWith("win")) {

    bat "cd build_tools && \
      call python configure.py ${getConfigArgs(platform, license)} && \
      call python make.py"

  } else if (platform in ["mac_64", "linux_64"]) {

    sh "cd build_tools && \
      ./configure.py ${getConfigArgs(platform, license)} && \
      ./make.py"
    isUnix = true

  }

  if (license == "opensource") {
    String os, arch, version
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

    Closure coreDeployPath = {
      return "${env.S3_BUCKET}/${os}/core/${branch}/${it}/${arch}"
    }

    String cmdUpload = """
      aws s3 cp --acl public-read --no-progress \
        build_tools/out/${platform}/onlyoffice/core/core.7z \
        s3://${coreDeployPath(version)}/
      aws s3 sync --delete --acl public-read --no-progress \
        s3://${coreDeployPath(version)}/ \
        s3://${coreDeployPath('latest')}/
    """

    if (isUnix) sh cmdUpload else bat cmdUpload
  }
}

// Build Packages

void buildEditors (String platform) {
  if (platform.startsWith("win")) {

    bat "cd desktop-apps && \
      make clean-package && \
      make deploy"

    def deployData = readJSON file: "desktop-apps/win-linux/package/windows/deploy.json"
    if (deployMap.editors == null) deployMap.editors = []
    deployMap.editors.addAll(deployData.items)

  } else if (platform == "mac_64") {

    sh "cd build_tools && ./make_packages.py"

    sh """#!/bin/bash -xe
      cd desktop-apps/macos/build

      PACKAGE_NAME="ONLYOFFICE\${_X86:+"-x86"}"
      DEPLOY_TITLE="macOS\${_X86:+" x86"}"

      S3_SECTION_DIR="onlyoffice/\$RELEASE_BRANCH/macos"
      S3_UPDATES_DIR="\$S3_SECTION_DIR/update/editors\${_X86:+"_x86"}/\$PRODUCT_VERSION.\$BUILD_NUMBER"
      APP_VERSION=\$(mdls -name kMDItemVersion -raw ONLYOFFICE.app)
      DMG="\$PACKAGE_NAME-\$PRODUCT_VERSION-\$BUILD_NUMBER.dmg"
      ZIP="\$PACKAGE_NAME-\$APP_VERSION.zip"
      APPCAST="onlyoffice.xml"
      CHANGES_EN="\$PACKAGE_NAME-\$APP_VERSION.html"
      CHANGES_RU="\$PACKAGE_NAME-\$APP_VERSION.ru.html"

      aws s3 cp --no-progress --acl public-read \
        ONLYOFFICE.dmg s3://\$S3_BUCKET/\$S3_SECTION_DIR/\$DMG

      aws s3 sync --no-progress --acl public-read \
        update s3://\$S3_BUCKET/\$S3_UPDATES_DIR

      echo -e "platform,title,path" > deploy.csv
      echo -e "macos,\$DEPLOY_TITLE DMG,\$S3_SECTION_DIR/\$DMG" >> deploy.csv
      echo -e "macos,\$DEPLOY_TITLE ZIP,\$S3_UPDATES_DIR/\$ZIP" >> deploy.csv
      for i in update/*.delta; do
        DELTA=\$(basename \$i)
        echo -e "macos,\$DEPLOY_TITLE \$DELTA,\$S3_UPDATES_DIR/\$DELTA" >> deploy.csv
      done
      echo -e "macos,\$DEPLOY_TITLE Appcast,\$S3_UPDATES_DIR/\$APPCAST" >> deploy.csv
      if [[ -f update/\$CHANGES_EN ]]; then
        echo -e "macos,\$DEPLOY_TITLE Release Notes EN,\$S3_UPDATES_DIR/\$CHANGES_EN" >> deploy.csv
      fi
      if [[ -f update/\$CHANGES_RU ]]; then
        echo -e "macos,\$DEPLOY_TITLE Release Notes RU,\$S3_UPDATES_DIR/\$CHANGES_RU" >> deploy.csv
      fi
    """

    def deployData = readCSV file: "desktop-apps/macos/build/deploy.csv", format: CSVFormat.DEFAULT.withHeader()
    for(item in deployData) {
      def temp = [ 
        platform: item.get('platform'),
        title: item.get('title'),
        path: item.get('path') ]
      deployMap.editors.add(temp)
    }

  } else if (platform == "linux_64") {

    sh "cd desktop-apps/win-linux/package/linux && \
      make clean && \
      make deploy"

    def deployData = readJSON file: "desktop-apps/win-linux/package/linux/deploy.json"
    deployMap.editors.addAll(deployData.items)

  }
}

void buildBuilder(String platform) {
  if (platform in ["win_64", "win_32"]) {

    bat "cd document-builder-package && \
      make clean && \
      make deploy"

  } else if (platform == "linux_64") {

    sh "cd document-builder-package && \
      make clean && \
      make deploy"

  }

  def deployData = readJSON file: "document-builder-package/deploy.json"
  deployMap.builder.addAll(deployData.items)
}

void buildServer(String platform = 'native', String edition='community') {
  String productName
  switch(edition) {
    case "community":   productName = "DocumentServer"; break
    case "enterprise":  productName = "DocumentServer-EE"; break
    case "developer":   productName = "DocumentServer-DE"; break
  }

  if (platform == "win_64") {

    bat "cd document-server-package && \
      set \"PRODUCT_NAME=${productName}\" && \
      make clean && \
      make deploy"

  } else if (platform == "linux_64") {

    sh "cd document-server-package && \
      export PRODUCT_NAME=${productName.toLowerCase()} && \
      make clean && \
      make deploy"

    sh "cd Docker-DocumentServer && \
      export PRODUCT_NAME=${productName.toLowerCase()} && \
      make clean && \
      make deploy"

  }

  def deployData = readJSON file: "document-server-package/deploy.json"
  switch(edition) {
    case "community":  deployMap.server_ce.addAll(deployData.items); break
    case "enterprise": deployMap.server_ee.addAll(deployData.items); break
    case "developer":  deployMap.server_de.addAll(deployData.items); break
  }
}

void buildAndroid(String branch = 'master', String config = 'release') {
  if (params.wipe) {
    sh "docker image rm -f onlyoffice/android-core-builder"
  }

  sh """#!/bin/bash -xe
    [[ ! -d android/workspace ]] && mkdir -p android/workspace
    cd android

    rm -rf \
      workspace/build_tools/out \
      index.html \
      *.zip
  """

  def dockerRunOptions = []
  dockerRunOptions.add("-e BUILD_BRANCH=${branch}")
  dockerRunOptions.add("-e BUILD_CONFIG=${config}")
  dockerRunOptions.add("-v ${env.WORKSPACE}/android/workspace:/home/user")

  docker.image('onlyoffice/android-core-builder:latest').withRun(dockerRunOptions.join(' ')) { c ->
    sh "docker logs -f ${c.id}"
  }

  String androidLibsFile = "android-libs-${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}.zip"
  String androidLibsUri = "onlyoffice/${env.RELEASE_BRANCH}/android/${androidLibsFile}"

  sh """#!/bin/bash -xe
    cd android
    pushd workspace/build_tools/out
    zip -r ../../../${androidLibsFile} ./android* ./js
    popd

    aws s3 cp --no-progress --acl public-read \
      ${androidLibsFile} s3://\$S3_BUCKET/${androidLibsUri}
  """

  deployMap.android.add([
    platform: 'android',
    title: 'Android libs',
    path: androidLibsUri
  ])
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

void createReports() {
  Boolean editors = !deployMap.editors.isEmpty()
  Boolean builder = !deployMap.builder.isEmpty()
  Boolean server_ce = !deployMap.server_ce.isEmpty()
  Boolean server_ee = !deployMap.server_ee.isEmpty()
  Boolean server_de = !deployMap.server_de.isEmpty()
  Boolean android = !deployMap.android.isEmpty()

  dir ("html") {
    deleteDir()

    sh """
      test -f style.css || wget -nv https://unpkg.com/style.css -O style.css
      test -f custom.css || echo \"body { margin: 16px; }\" > custom.css
    """

    if (editors) {
      writeFile file: "editors.html", text: genHtml(deploy.editors)
      publishReport("DesktopEditors", "editors.html")
    }
    if (builder) {
      writeFile file: "builder.html", text: genHtml(deploy.builder)
      publishReport("DocumentBuilder", "builder.html")
    }
    if (server_ce || server_ee || server_de) {
      ArrayList serverIndexFiles = []
      if (server_ce) {
        writeFile file: "server_ce.html", text: genHtml(deploy.server_ce)
        serverIndexFiles.add("server_ce.html")
      }
      if (server_ee) {
        writeFile file: "server_ee.html", text: genHtml(deploy.server_ee)
        serverIndexFiles.add("server_ee.html")
      }
      if (server_de) {
        writeFile file: "server_de.html", text: genHtml(deploy.server_de)
        serverIndexFiles.add("server_de.html")
      }
      publishReport("DocumentServer", serverIndexFiles.join(","))
    }
    if (android) {
      writeFile file: "android.html", text: genHtml(deploy.android)
      publishReport("Android", "android.html")
    }
  }
}

def genHtml(ArrayList deployList) {
  String url = ''
  String html = """\
    |<html>
    |<head>
    |  <link rel="stylesheet" href="style.css">
    |  <link rel="stylesheet" href="custom.css">
    |<head>
    |<body>
    |  <dl>
    |""".stripMargin()

  for(p in deployList) {
    url = "https://s3.eu-west-1.amazonaws.com/${env.S3_BUCKET}/${p.path}"
    html += """\
      |    <dt>${p.title}</dt>
      |    <dd><a href="${url}" target="_blank">${p.path}</a></dd>
      |""".stripMargin()
  }

  html += """\
    |  </dl>
    |</body>
    |</html>
    |""".stripMargin()

  return html
}

void publishReport(String title, String files, String dir = '') {
  publishHTML([
    allowMissing: false,
    alwaysLinkToLastBuild: false,
    includes: "${files},*.css",
    keepAll: true,
    reportDir: dir,
    reportFiles: files,
    reportName: title,
    reportTitles: ''
  ])
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
  sh label: "Send Telegram Message", script: "curl -X POST -s -S \
    -d chat_id=${chatId} \
    ${markdown ? '-d parse_mode=markdown' : ''} \
    -d disable_web_page_preview=true \
    --data-urlencode text='${text}' \
    https://api.telegram.org/bot\$TELEGRAM_TOKEN/sendMessage"
}
