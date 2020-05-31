def checkoutRepo(String repo, String branch = 'master', String dir, String company = 'ONLYOFFICE') {
    checkout([
            $class: 'GitSCM',
            branches: [[
                    name: branch
                ]
            ],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[
                    $class: 'RelativeTargetDirectory',
                    relativeTargetDir: dir
                ]
            ],
            submoduleCfg: [],
            userRemoteConfigs: [[
                    url: "git@github.com:${company}/${repo}.git"
                ]
            ]
        ]
    )
}

return this

def getReposList()
{
    def repos = []
    repos.add('build_tools')
    repos.add('core')
    repos.add('core-fonts')
    repos.add('desktop-apps')
    repos.add('desktop-sdk')
    repos.add('dictionaries')
    repos.add('document-builder-package')
    repos.add('document-server-integration')
    repos.add('document-server-package')
    repos.add('r7')
    repos.add('plugin-ocr')
    repos.add('plugin-macros')
    repos.add('plugin-highlightcode')
    repos.add('plugin-photoeditor')
    repos.add('plugin-youtube')
    repos.add('plugin-speech')
    repos.add('plugin-thesaurus')
    repos.add('plugin-translator')
    repos.add('plugin-autocomplete')
    repos.add('plugin-easybib')
    repos.add('plugin-wordpress')
    repos.add('plugin-zotero')
    repos.add('plugin-mendeley')
    repos.add('sdkjs')
    repos.add('sdkjs-comparison')
    repos.add('sdkjs-content-controls')
    repos.add('server')
    repos.add('server-license')
    repos.add('server-lockstorage')
    repos.add('web-apps')
    repos.add('web-apps-mobile')
    repos.add('Docker-DocumentServer')
    repos.add('DocumentBuilder')
    return repos
}

def checkoutRepos(String branch = 'master')
{    
    for (repo in getReposList()) {
        String dir = !repo.startsWith("plugin-") ? repo : "sdkjs-plugins/${repo}"
        String company = (repo != "r7") ? "ONLYOFFICE" : "ASC-OFFICE"
        checkoutRepo(repo, branch, dir, company)
    }

    return this
}

def tagRepos(String tag)
{
    for (repo in getReposList()) {
        String dir = !repo.startsWith("plugin-") ? repo : "sdkjs-plugins/${repo}"
        sh "cd ${dir} && \
            git tag -l | xargs git tag -d && \
            git fetch --tags && \
            git tag ${tag} && \
	        git push origin --tags"
    }

    return this
}

def getConfParams(String platform, Boolean clean, Boolean noneFree)
{
    def modules = []
    if (params.core) {
        modules.add('core')
    }
    if (params.desktopeditor) {
        modules.add('desktop')
    }
    if (params.documentbuilder) {
        modules.add('builder')
    }
    if (params.documentserver||params.documentserver_ie||params.documentserver_de) {
        modules.add('server')
    }
    if (platform.startsWith("win")) {
        modules.add('tests')
        modules.add('updmodule')
    }

    def confParams = []
    confParams.add("--module \"${modules.join(' ')}\"")
    confParams.add("--platform ${platform}")
    confParams.add("--update false")
    confParams.add("--clean ${clean.toString()}")
    confParams.add("--qt-dir ${env.QT_PATH}")
    if (platform.endsWith("_xp")) {
        confParams.add("--qt-dir-xp ${env.QT56_PATH}")
    }
    if (noneFree) {
        confParams.add("--sdkjs-addon comparison")
        confParams.add("--sdkjs-addon content-controls")
        confParams.add("--server-addon license")
        confParams.add("--server-addon lockstorage")
        confParams.add("--web-apps-addon mobile")
    }
    if (params.extra_params) {
        confParams.add(params.extra_params)
    }

    confParams.add("--branding r7")
    confParams.add("--branding-name r7-office")

    return confParams.join(' ')
}

def linuxBuild(String platform = 'native', Boolean clean = true, Boolean noneFree = false)
{
    sh "cd build_tools && \
        ./configure.py ${getConfParams(platform, clean, noneFree)} &&\
        ./make.py"

    return this
}

def linuxBuildDesktop(String platform = 'native')
{
    sh "cd desktop-apps/win-linux/package/linux &&\
         make clean &&\
         make deploy -e BRANDING_DIR=../../../../r7/desktop-apps/win-linux/package/linux"
    publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            includes: 'index.html',
            keepAll: true,
            reportDir: 'desktop-apps/win-linux/package/linux',
            reportFiles: 'index.html',
            reportName: "DesktopEditors(${platform})",
            reportTitles: ''
        ]
    )

    return this
}

def linuxBuildBuilder(String platform = 'native')
{
    sh "cd document-builder-package &&\
         make clean &&\
         make deploy -e BRANDING_DIR=../r7/document-builder-package"

    publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            includes: 'index.html',
            keepAll: true,
            reportDir: 'document-builder-package',
            reportFiles: 'index.html',
            reportName: "DocumentBuilder(${platform})",
            reportTitles: ''
        ]
    )

    return this
}

def linuxBuildServer(String platform = 'native', String productName='documentserver')
{
    sh "cd document-server-package && \
        export PRODUCT_NAME=${productName} && \
        make clean && \
        make deploy -e BRANDING_DIR=../r7/document-server-package"

    sh "cd Docker-DocumentServer && \
        export PRODUCT_NAME=${productName} && \
        export ONLYOFFICE_VALUE=ds && \
        make clean && \
        make deploy"

    publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: false,
            includes: 'index.html',
            keepAll: true,
            reportDir: 'document-server-package',
            reportFiles: 'index.html',
            reportName: "DocumentServer(${platform})",
            reportTitles: ''
        ]
    )

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
    checkoutRepo('doc-builder-testing', 'master', 'doc-builder-testing')
    sh "docker rmi doc-builder-testing || true"
    sh "cd doc-builder-testing &&\
        docker build --tag doc-builder-testing -f dockerfiles/debian-develop/Dockerfile . &&\
        docker run --rm doc-builder-testing bundle exec parallel_rspec spec -n 2"

    return this
}

def windowsBuild(String platform = 'native', Boolean clean = true, Boolean noneFree = false)
{
    bat "cd build_tools &&\
            call python configure.py ${getConfParams(platform, clean, noneFree)} &&\
            call python make.py"

    return this
}

def windowsBuildDesktop (String platform)
{
    bat "cd desktop-apps &&\
            mingw32-make clean-package &&\
            mingw32-make deploy -e BRANDING_DIR=../r7/desktop-apps"

    publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            includes: 'index.html',
            keepAll: true,
            reportDir: 'desktop-apps/win-linux/package/windows',
            reportFiles: 'index.html',
            reportName: "DesktopEditors(${platform})",
            reportTitles: ''
        ]
    )

    return this
}

def windowsBuildBuilder(String platform)
{
    bat "cd document-builder-package &&\
        mingw32-make clean &&\
        mingw32-make deploy -e BRANDING_DIR=../r7/document-builder-package"

    publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: false,
            includes: 'index.html',
            keepAll: true,
            reportDir: 'document-builder-package',
            reportFiles: 'index.html',
            reportName: "DocumentBuilder(${platform})",
            reportTitles: ''
        ]
    )

    return this
}

def windowsBuildServer(String platform = 'native', String productName='DocumentServer')
{
    bat "cd document-server-package && \
        set \"PRODUCT_NAME=${productName}\" && \
        mingw32-make clean && \
        mingw32-make deploy -e BRANDING_DIR=../r7/document-server-package"

    publishHTML([
            allowMissing: true,
            alwaysLinkToLastBuild: false,
            includes: 'index.html',
            keepAll: true,
            reportDir: 'document-server-package',
            reportFiles: 'index.html',
            reportName: "DocumentServer(${platform})",
            reportTitles: ''
        ]
    )

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
        mingw32-make deploy"

    return this
}
