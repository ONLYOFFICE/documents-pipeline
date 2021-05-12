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
    [name: 'document-templates'],
    [name: 'documents-pipeline'],
    [name: 'onlyoffice'],
    [name: 'plugin-ocr',           dir: 'sdkjs-plugins/plugin-ocr'],
    [name: 'plugin-macros',        dir: 'sdkjs-plugins/plugin-macros'],
    [name: 'plugin-highlightcode', dir: 'sdkjs-plugins/plugin-highlightcode'],
    [name: 'plugin-photoeditor',   dir: 'sdkjs-plugins/plugin-photoeditor'],
    [name: 'plugin-youtube',       dir: 'sdkjs-plugins/plugin-youtube'],
    [name: 'plugin-speech',        dir: 'sdkjs-plugins/plugin-speech'],
    [name: 'plugin-thesaurus',     dir: 'sdkjs-plugins/plugin-thesaurus'],
    [name: 'plugin-translator',    dir: 'sdkjs-plugins/plugin-translator'],
    [name: 'plugin-autocomplete',  dir: 'sdkjs-plugins/plugin-autocomplete'],
    [name: 'plugin-easybib',       dir: 'sdkjs-plugins/plugin-easybib'],
    [name: 'plugin-wordpress',     dir: 'sdkjs-plugins/plugin-wordpress'],
    [name: 'plugin-zotero',        dir: 'sdkjs-plugins/plugin-zotero'],
    [name: 'plugin-mendeley',      dir: 'sdkjs-plugins/plugin-mendeley'],
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

def checkoutRepos(String branch = 'master')
{    
    for (repo in listRepos) {
        checkoutRepo(repo, branch)
    }

    return this
}

def tagRepos(String tag)
{
    for (repo in listRepos) {
        sh "cd ${repo.dir} && \
            git tag -l | xargs git tag -d && \
            git fetch --tags && \
            git tag ${tag} && \
	        git push origin --tags"
    }

    return this
}

def getConfParams(String platform, String license)
{
    def modules = []
    if (params.core && license == "opensource") {
        modules.add('core')
    }
    if (platform != "mac_64") {
        // Add module to build to enforce clean it on build
        if (params.desktopeditor && ((license == "opensource" && params.clean)
            || license == "freemium")) {
            modules.add('desktop')
        }
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
    if (params.beta) {
        confParams.add("--beta 1")
    }
    if (!params.extra_params.isEmpty()) {
        confParams.add(params.extra_params)
    }

    return confParams.join(' ')
}

def linuxBuild(String platform = 'native', String license = 'opensource')
{
    sh "cd build_tools && \
        ./configure.py ${getConfParams(platform, license)} &&\
        ./make.py"

    return this
}

def linuxBuildDesktop(String platform = 'native')
{
    sh "cd desktop-apps/win-linux/package/linux &&\
         make clean &&\
         make deploy"

    def deployData = readJSON file: "desktop-apps/win-linux/package/linux/deploy.json"

    for(item in deployData.items) {
        println item
        deployDesktopList.add(item)
    }

    return this
}

def linuxBuildBuilder(String platform = 'native')
{
    sh "cd document-builder-package &&\
         make clean &&\
         make deploy"

    def deployData = readJSON file: "document-builder-package/deploy.json"

    for(item in deployData.items) {
        println item
        deployBuilderList.add(item)
    }

    return this
}

def linuxBuildServer(String platform = 'native', String productName='documentserver')
{
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

    return this
}

def linuxBuildCore()
{
    sh "cd core && \
        make deploy"

    return this
}

def linuxTest()
{
    checkoutRepo([owner: 'ONLYOFFICE', name: 'doc-builder-testing',
        dir: 'doc-builder-testing'], 'master')
    sh "docker rmi doc-builder-testing || true"
    sh "cd doc-builder-testing &&\
        docker build --tag doc-builder-testing -f dockerfiles/debian-develop/Dockerfile . &&\
        docker run --rm doc-builder-testing bundle exec parallel_rspec spec -n 2"

    return this
}

def macosBuild(String platform = 'native', String license = 'opensource') {
    sh "cd build_tools && \
        ./configure.py ${getConfParams(platform, license)} && \
        ./make.py"

    return this
}

def macosBuildDesktop(String platform = 'native') {
    sh "cd desktop-apps && \
        make clean && \
        make deploy"

    def deployData = readJSON file: "desktop-apps/deploy.json"
    for(item in deployData.items) {
        println item
        deployDesktopList.add(item)
    }

    return this
}

def macosBuildCore() {
    sh "cd core && make deploy"
    return this
}

def windowsBuild(String platform = 'native', String license = 'opensource')
{
    bat "cd build_tools &&\
            call python configure.py ${getConfParams(platform, license)} &&\
            call python make.py"

    return this
}

def windowsBuildDesktop (String platform)
{
    bat "cd desktop-apps &&\
            make clean-package &&\
            make deploy"

    def deployData = readJSON file: "desktop-apps/win-linux/package/windows/deploy.json"

    for(item in deployData.items) {
        println item
        deployDesktopList.add(item)
    }

    return this
}

def windowsBuildBuilder(String platform)
{
    bat "cd document-builder-package &&\
        make clean &&\
        make deploy"

    def deployData = readJSON file: "document-builder-package/deploy.json"

    for(item in deployData.items) {
        println item
        deployBuilderList.add(item)
    }

    return this
}

def windowsBuildServer(String platform = 'native', String productName='DocumentServer')
{
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

    return this
}

def windowsBuildCore(String platform)
{
    String winSdkVersion = '10.0.14393.0'
    String platformType
    
    switch (platform) {
        case 'win_64':
            platformType = 'x64'
            break
        case 'win_32':
            platformType = 'x86'
            break
        default:
            platformType = ''
    }

    bat "cd core && \
        call \"C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC\\vcvarsall.bat\" ${platformType} ${winSdkVersion} && \
        make deploy"

    return this
}

def androidBuild(String branch = 'master', String config = 'release')
{
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

    return this
}

def createReports()
{
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

    return this
}

def genHtml(ArrayList deployList)
{
    String url = ''
    String html = """\
        |<html>
        |<head>
        |   <link rel="stylesheet" href="style.css">
        |   <link rel="stylesheet" href="custom.css">
        |<head>
        |<body>
        |   <dl>
        |""".stripMargin()

    for(p in deployList) {
        url = "https://${env.S3_BUCKET}.s3-eu-west-1.amazonaws.com/${p.path}"
        html += """\
            |       <dt>${p.title}</dt>
            |       <dd><a href="${url}">${url}</a></dd>
            |""".stripMargin()
    }

    html += """\
        |   </dl>
        |</body>
        |</html>
        |""".stripMargin()

    return html
}

