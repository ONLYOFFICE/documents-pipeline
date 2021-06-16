def checkoutRepo(Map repo, String branch = 'master') {
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
  // [name: 'document-templates'],
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
  if (it.dir == null)   it.dir = it.name
}

return this

def checkoutRepos(String branch = 'master') {    
  for (repo in listRepos) {
    checkoutRepo(repo, branch)
  }
}

def tagRepos(String tag) {
  for (repo in listRepos) {
    sh "cd ${repo.dir} && \
      git tag -l | xargs git tag -d && \
      git fetch --tags && \
      git tag ${tag} && \
      git push origin --tags"
  }

  return this
}

def getConfParams(String platform, String license) {
  def modules = []
  if (params.core && license == "opensource") {
    modules.add('core')
  }
  // Add module to build to enforce clean it on build
  if (params.desktopeditor && ((license == "opensource"
    && params.clean) || license == "freemium")) {
    modules.add('desktop')
  }
  if (platform != "mac_64") {
    if (params.documentbuilder && license == "opensource") {
      modules.add('builder')
    }
    if ((params.documentserver && license == "opensource")
      || ((params.documentserver_ee || params.documentserver_ie
      || params.documentserver_de) && license == "commercial")) {
      modules.add('server')
    }
  }
  if (platform.startsWith("win")) {
    modules.add('tests')
  }

  def confParams = []
  confParams.add("--module \"${modules.join(' ')}\"")
  confParams.add("--platform ${platform}")
  confParams.add("--update false")
  confParams.add("--clean ${params.clean.toString()}")
  confParams.add("--qt-dir ${env.QT_PATH}")
  if (platform.endsWith("_xp")) {
    confParams.add("--qt-dir-xp ${env.QT56_PATH}")
  }
  if (license == "freemium" || license == "commercial") {
    confParams.add("--branding onlyoffice")
  }
  if (platform == "mac_64") {
    confParams.add("--branding-name \"onlyoffice\"")
    confParams.add("--compiler \"clang\"")
    if (env._X86 == "1") confParams.add("--config \"use_v8\"")
  }
  if (params.beta || env.BRANCH_NAME == 'develop') {
    confParams.add("--beta 1")
  }
  if (!params.extra_params.isEmpty()) {
    confParams.add(params.extra_params)
  }

  return confParams.join(' ')
}

def linuxBuild(String platform = 'native', String license = 'opensource') {
  sh "cd build_tools && \
    ./configure.py ${getConfParams(platform, license)} &&\
    ./make.py"
}

def linuxBuildDesktop(String platform = 'native') {
  sh "cd desktop-apps/win-linux/package/linux &&\
    make clean &&\
    make deploy"

  def deployData = readJSON file: "desktop-apps/win-linux/package/linux/deploy.json"

  for(item in deployData.items) {
    println item
    deployDesktopList.add(item)
  }
}

def linuxBuildBuilder(String platform = 'native') {
  sh "cd document-builder-package &&\
    make clean &&\
    make deploy"

  def deployData = readJSON file: "document-builder-package/deploy.json"

  for(item in deployData.items) {
    println item
    deployBuilderList.add(item)
  }
}

def linuxBuildServer(String platform = 'native', String productName='documentserver') {
  sh "cd document-server-package && \
    export PRODUCT_NAME=${productName} && \
    make clean && \
    make deploy"

  sh "cd Docker-DocumentServer && \
    export PRODUCT_NAME=${productName} && \
    make clean && \
    make deploy"

  def deployData = readJSON file: "document-server-package/deploy.json"

  for(item in deployData.items) {
    println item
    switch(productName) {
      case 'documentserver':
        deployServerCeList.add(item)
        break
      case 'documentserver-ee':
        deployServerEeList.add(item)
        break
      case 'documentserver-de':
        deployServerDeList.add(item)
        break
    }
  }
}

def linuxTest() {
  checkoutRepo([owner: 'ONLYOFFICE', name: 'doc-builder-testing',
    dir: 'doc-builder-testing'], 'master')
  sh "docker rmi doc-builder-testing || true"
  sh "cd doc-builder-testing &&\
    docker build --tag doc-builder-testing -f dockerfiles/debian-develop/Dockerfile . &&\
    docker run --rm doc-builder-testing bundle exec parallel_rspec spec -n 2"
}

def macosBuild(String platform = 'native', String license = 'opensource') {
  sh "cd build_tools && \
    ./configure.py ${getConfParams(platform, license)} && \
    ./make.py"
}

def macosBuildDesktop(String platform = 'native') {
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
    println temp
    deployDesktopList.add(temp)
  }
}

def windowsBuild(String platform = 'native', String license = 'opensource') {
  bat "cd build_tools &&\
    call python configure.py ${getConfParams(platform, license)} &&\
    call python make.py"
}

def windowsBuildDesktop (String platform) {
  bat "cd desktop-apps &&\
    make clean-package &&\
    make deploy"

  def deployData = readJSON file: "desktop-apps/win-linux/package/windows/deploy.json"

  for(item in deployData.items) {
    println item
    deployDesktopList.add(item)
  }
}

def windowsBuildBuilder(String platform) {
  bat "cd document-builder-package &&\
    make clean &&\
    make deploy"

  def deployData = readJSON file: "document-builder-package/deploy.json"

  for(item in deployData.items) {
    println item
    deployBuilderList.add(item)
  }
}

def windowsBuildServer(String platform = 'native', String productName='DocumentServer') {
  bat "cd document-server-package && \
    set \"PRODUCT_NAME=${productName}\" && \
    make clean && \
    make deploy"

  def deployData = readJSON file: "document-server-package/deploy.json"

  for(item in deployData.items) {
    println item
    switch(productName) {
      case 'DocumentServer':
        deployServerCeList.add(item)
        break
      case 'DocumentServer-EE':
        deployServerEeList.add(item)
        break
      case 'DocumentServer-DE':
        deployServerDeList.add(item)
        break
    }
  }
}

def androidBuild(String branch = 'master', String config = 'release') {
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

  def deployData = [ platform: 'android', title: 'Android libs', path: androidLibsUri ]

  println deployData
  deployAndroidList.add(deployData)
}

def deployCore(String platform) {
  String dirRepo, platformType, version

  switch(platform) {
    case 'linux_64':
      dirRepo = 'linux'
      platformType = 'x64'
      version = PRODUCT_VERSION + '-' + BUILD_NUMBER
      break
    case 'mac_64':
      dirRepo = 'mac'
      platformType = 'x64'
      version = PRODUCT_VERSION + '-' + BUILD_NUMBER
      break
    case 'win_64':
      dirRepo = 'windows'
      platformType = 'x64'
      version = PRODUCT_VERSION + '.' + BUILD_NUMBER
      break
    case 'win_32':
      dirRepo = 'windows'
      platformType = 'x86'
      version = PRODUCT_VERSION + '.' + BUILD_NUMBER
      break
  }

  String pathCore = "build_tools/out/${platform}/onlyoffice/core/core.7z"
  String dirS3CoreV = "${dirRepo}/core/${BRANCH_NAME}/${version}/${platformType}"
  String dirS3CoreL = "${dirRepo}/core/${BRANCH_NAME}/latest/${platformType}"
  String label = 'Deploy Core to S3'
  String script = """
    aws s3 cp --acl public-read --no-progress \
      ${pathCore} \
      s3://${S3_BUCKET}/${dirS3CoreV}/
    aws s3 sync --acl public-read --delete --no-progress \
      s3://${S3_BUCKET}/${dirS3CoreV}/ \
      s3://${S3_BUCKET}/${dirS3CoreL}/
  """

  switch(platform) {
    case ['linux_64', 'mac_64']: sh  label: label, script: script; break
    case ['win_64', 'win_32']:   bat label: label, script: script; break
  }
}

def createReports() {
  Boolean desktop = !deployDesktopList.isEmpty()
  Boolean builder = !deployBuilderList.isEmpty()
  Boolean serverc = !deployServerCeList.isEmpty()
  Boolean servere = !deployServerEeList.isEmpty() 
  Boolean serverd = !deployServerDeList.isEmpty()
  Boolean android = !deployAndroidList.isEmpty()

  dir ('html') {
    deleteDir()

    sh "wget -nv https://unpkg.com/style.css -O style.css"
    sh "echo \"body { margin: 16px; }\" > custom.css"

    if (desktop) { writeFile file: 'desktopeditors.html', text: genHtml(deployDesktopList) }
    if (builder) { writeFile file: 'documentbuilder.html', text: genHtml(deployBuilderList) }
    if (serverc) { writeFile file: 'documentserver_ce.html', text: genHtml(deployServerCeList) }
    if (servere) { writeFile file: 'documentserver_ee.html', text: genHtml(deployServerEeList) }
    if (serverd) { writeFile file: 'documentserver_de.html', text: genHtml(deployServerDeList) }
    if (android) { writeFile file: 'android.html', text: genHtml(deployAndroidList) }
  }

  if (desktop) {
    publishHTML([
      allowMissing: false,
      alwaysLinkToLastBuild: false,
      includes: 'desktopeditors.html,*.css',
      keepAll: true,
      reportDir: 'html',
      reportFiles: 'desktopeditors.html',
      reportName: "DesktopEditors",
      reportTitles: ''
    ])
  }
  
  if (builder) {
    publishHTML([
      allowMissing: false,
      alwaysLinkToLastBuild: false,
      includes: 'documentbuilder.html,*.css',
      keepAll: true,
      reportDir: 'html',
      reportFiles: 'documentbuilder.html',
      reportName: "DocumentBuilder",
      reportTitles: ''
    ])
  }

  if (serverc || servere || serverd) {
    // compatibility for htmlpublisher-1.18
    def serverIndexFiles = []
    if (serverc) { serverIndexFiles.add('documentserver_ce.html') }
    if (servere) { serverIndexFiles.add('documentserver_ee.html') }
    if (serverd) { serverIndexFiles.add('documentserver_de.html') }

    publishHTML([
      allowMissing: false,
      alwaysLinkToLastBuild: false,
      includes: 'documentserver_*.html,*.css',
      keepAll: true,
      reportDir: 'html',
      reportFiles: serverIndexFiles.join(','),
      reportName: "DocumentServer",
      reportTitles: ''
    ])
  }

  if (android) {
    publishHTML([
      allowMissing: false,
      alwaysLinkToLastBuild: false,
      includes: 'android.html,*.css',
      keepAll: true,
      reportDir: 'html',
      reportFiles: 'android.html',
      reportName: "Android",
      reportTitles: ''
    ])
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
    url = "https://${env.S3_BUCKET}.s3-eu-west-1.amazonaws.com/${p.path}"
    html += """\
      |    <dt>${p.title}</dt>
      |    <dd><a href="${url}">${url}</a></dd>
      |""".stripMargin()
  }

  html += """\
    |  </dl>
    |</body>
    |</html>
    |""".stripMargin()

  return html
}

def setStageStats(String stageStatus) {
  if (stageStats."${STAGE_NAME}" == null) {
    stageStats."${STAGE_NAME}" = stageStatus
  }
}

def getJobStats(String jobStatus) {
  String text = "Build [${currentBuild.fullDisplayName}]" \
    + "(${currentBuild.absoluteUrl}) ${jobStatus}"
  String icon
  stageStats.each { stage, status ->
    switch(status) {
      case 'fixed':   icon = '🟢'; break
      case 'failure': icon = '🔴'; break
      case 'success': icon = '🔵'; break
    }
    text += "\n${icon} ${stage}"
  }
  return text
}

def sendTelegramMessage(String text, String chatId, Boolean markdown = true) {
  sh label: "Send Telegram Message", script: "curl -X POST -s -S \
    ${markdown ? '-d parse_mode=markdown' : ''} \
    -d chat_id=${chatId} \
    --data-urlencode text='${text}' \
    https://api.telegram.org/bot\$TELEGRAM_TOKEN/sendMessage"
}
