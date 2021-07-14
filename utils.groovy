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
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"
  String product = "editors"
  String fplatform

  if (platform.startsWith("win")) {

    bat "cd desktop-apps && \
      make clean-package && \
      make packages"

    if (platform.startsWith("win_64") fplatform = "Windows x64" else
    if (platform.startsWith("win_32") fplatform = "Windows x86"

    dir ("desktop-apps/win-linux/package/windows") {
      uploadFiles("*.exe", "windows/", product, fplatform, "Installer")
      uploadFiles("*.zip", "windows/", product, fplatform, "Portable")
      uploadFiles("update/*.exe,update/*.xml,update/*.html",
        "windows/editors/${version}/", product, fplatform, "WinSparkle")
    }

  } else if (platform == "mac_64") {

    sh "cd build_tools && ./make_packages.py"

    String package = (env._X86 != '1') ? "ONLYOFFICE" : "ONLYOFFICE-x86"
    String subdir = (env._X86 != '1') ? "editors_x64" : "editors_x86"
    fplatform = (env._X86 != '1') ? "macOS x64" : "macOS x86"

    dir ("desktop-apps/macos/build") {
      uploadFiles("${package}.dmg",
        "macos/${package}-${version}.dmg", product, fplatform, "Disk Image")
      uploadFiles("update/*.zip,update/*.delta,update/*.xml,update/*.html",
        "macos/${subdir}/${version}/", product, fplatform, "Sparkle")
    }

  } else if (platform == "linux_64") {

    sh "cd desktop-apps/win-linux/package/linux && \
      make clean && \
      make packages"

    fplatform = "Linux x64"

    dir ("desktop-apps/win-linux/package/linux") {
      uploadFiles("deb/*.deb",        "ubuntu/",   product, fplatform, "Ubuntu")
      uploadFiles("rpm/**/*.rpm",     "centos/",   product, fplatform, "CentOS")
      uploadFiles("apt-rpm/**/*.rpm", "altlinux/", product, fplatform, "AltLinux")
      uploadFiles("urpmi/**/*.rpm",   "rosa/",     product, fplatform, "Rosa")
      uploadFiles("tar/*.tar.gz",     "linux/",    product, fplatform, "Portable")
      // uploadFiles("deb-astra/*.deb", "astralinux/", product, fplatform, "AstraLinux Signed")
    }

  }
}

void buildBuilder(String platform) {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"
  String product = "editors"
  String fplatform

  if (platform.startsWith("win")) {

    bat "cd document-builder-package && \
      make clean && \
      make packages"

    fplatform = "Windows x64"

    dir ("document-builder-package") {
      uploadFiles("exe/*.exe", "windows/", product, fplatform, "Installer")
      uploadFiles("zip/*.zip", "windows/", product, fplatform, "Portable")
    }

  } else if (platform == "linux_64") {

    sh "cd document-builder-package && \
      make clean && \
      make packages"

    fplatform = "Linux x64"

    dir ("document-builder-package") {
      uploadFiles("deb/*.deb",    "ubuntu/", product, fplatform, "Ubuntu")
      uploadFiles("rpm/**/*.rpm", "centos/", product, fplatform, "CentOS")
      uploadFiles("tar/*.tar.gz", "linux/",  product, fplatform, "Portable")
    }

  }
}

void buildServer(String platform, String edition='community') {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"
  String product, productName

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

    dir ("document-server-package") {
      uploadFiles("exe/*.exe", "windows/", product, fplatform, "Installer")
    }

  } else if (platform == "linux_64") {

    sh "cd document-server-package && \
      export PRODUCT_NAME=${productName.toLowerCase()} && \
      make clean && \
      make packages"

    fplatform = "Linux x64"

    dir ("document-server-package") {
      uploadFiles("deb/*.deb",        "ubuntu/",   product, fplatform, "Ubuntu")
      uploadFiles("rpm/**/*.rpm",     "centos/",   product, fplatform, "CentOS")
      uploadFiles("apt-rpm/**/*.rpm", "altlinux/", product, fplatform, "AltLinux")
      uploadFiles("*.tar.gz",         "linux/",    product, fplatform, "Portable")
    }

    sh "cd Docker-DocumentServer && \
      export PRODUCT_NAME=${productName.toLowerCase()} && \
      make clean && \
      make deploy"

  }
}

void buildAndroid(String branch = 'master', String config = 'release') {
  String version = "${env.PRODUCT_VERSION}-${env.BUILD_NUMBER}"

  if (params.wipe) sh "docker image rm -f onlyoffice/android-core-builder"

  sh """#!/bin/bash -xe
    [[ ! -d android/workspace ]] && mkdir -p android/workspace
    cd android

    rm -rf workspace/build_tools/out *.zip
  """

  def dockerRunOptions = []
  dockerRunOptions.add("-e BUILD_BRANCH=${branch}")
  dockerRunOptions.add("-e BUILD_CONFIG=${config}")
  dockerRunOptions.add("-v ${env.WORKSPACE}/android/workspace:/home/user")

  docker.image('onlyoffice/android-core-builder:latest').withRun(dockerRunOptions.join(' ')) { c ->
    sh "docker logs -f ${c.id}"
  }

  sh "cd android/workspace/build_tools/out && \
    zip -r ../../../android-libs-${version}.zip ./android* ./js"

  dir ("android") {
    uploadFiles("*.zip", "android/", "android", "Android", "Libs")
  }
}

// Upload

void uploadFiles(String glob, String dest, String product, String platform, String section) {
  String s3uri
  Closure cmdUpload = { local, remote ->
    String cmd = "aws s3 cp --acl public-read --no-progress ${local} s3://${remote}"
    if (platform ==~ /^Windows.*/) bat cmd else sh cmd
  }
  Closure cmdMd5sum = {
    if (platform ==~ /^Windows.*/) {
      return bat (script: "md5sum ${it} | cut -c -32", returnStdout: true).trim()
    } else if (platform ==~ /^macOS.*/) {
      return sh (script: "md5 -qs ${it}", returnStdout: true).trim()
    } else {
      return sh (script: "md5sum ${it} | cut -c -32", returnStdout: true).trim()
    }
  }

  findFiles(glob: glob).each {
    s3uri = "${s3deploy}/${dest}${dest.endsWith('/') ? it.name : ''}"
    cmdUpload(it.path, s3uri)

    listDeploy.add([
      product: product,
      platform: platform,
      section: section,
      path: s3uri,
      file: it.name,
      size: it.length,
      md5: cmdMd5sum(it.path)
      // sha256: cmdSha256sum(it.path)
    ])
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

  Boolean editors = deploy.editors != null
  Boolean builder = deploy.builder != null
  Boolean server_ce = deploy.server_ce != null
  Boolean server_ee = deploy.server_ee != null
  Boolean server_de = deploy.server_de != null
  Boolean android = deploy.android != null

  dir ("html") {
    sh """
      rm -fv *.html
      test -f style.css || wget -nv https://unpkg.com/style.css -O style.css
    """

    if (editors)
      publishReport("DesktopEditors", ["editors.html": deploy.editors])
    if (builder)
      publishReport("DocumentBuilder", ["builder.html": deploy.builder])
    if (server_ce || server_ee || server_de) {
      Map serverReports
      if (server_ce) serverReports."server_ce.html" = deploy.server_ce
      if (server_ee) serverReports."server_ee.html" = deploy.server_ee
      if (server_de) serverReports."server_de.html" = deploy.server_de
      publishReport("DocumentServer", serverReports)
    }
    if (android)
      publishReport("Android", ["android.html": deploy.android])
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
  data.groupBy { it.platform }.each { platform, sections ->
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
  sh label: "Send Telegram Message", script: "curl -X POST -s -S \
    -d chat_id=${chatId} \
    ${markdown ? '-d parse_mode=markdown' : ''} \
    -d disable_web_page_preview=true \
    --data-urlencode text='${text}' \
    https://api.telegram.org/bot\$TELEGRAM_TOKEN/sendMessage"
}
