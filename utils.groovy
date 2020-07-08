def checkoutRepo(String repo, String branch = 'master', String dir = repo, String company = 'ONLYOFFICE') {
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

def getRepoMap(String name, String dir = name, String owner = 'ONLYOFFICE')
{
    return [owner: owner, name: name, dir: dir]
}

def getReposList()
{
    def repos = []
    repos.add(getRepoMap('build_tools'))
    repos.add(getRepoMap('core'))
    repos.add(getRepoMap('core-fonts'))
    repos.add(getRepoMap('desktop-apps'))
    repos.add(getRepoMap('desktop-sdk'))
    repos.add(getRepoMap('dictionaries'))
    repos.add(getRepoMap('document-builder-package'))
    repos.add(getRepoMap('document-server-integration'))
    repos.add(getRepoMap('document-server-package'))
    repos.add(getRepoMap('plugin-ocr',           'sdkjs-plugins/plugin-ocr'))
    repos.add(getRepoMap('plugin-macros',        'sdkjs-plugins/plugin-macros'))
    repos.add(getRepoMap('plugin-highlightcode', 'sdkjs-plugins/plugin-highlightcode'))
    repos.add(getRepoMap('plugin-photoeditor',   'sdkjs-plugins/plugin-photoeditor'))
    repos.add(getRepoMap('plugin-youtube',       'sdkjs-plugins/plugin-youtube'))
    repos.add(getRepoMap('plugin-speech',        'sdkjs-plugins/plugin-speech'))
    repos.add(getRepoMap('plugin-thesaurus',     'sdkjs-plugins/plugin-thesaurus'))
    repos.add(getRepoMap('plugin-translator',    'sdkjs-plugins/plugin-translator'))
    repos.add(getRepoMap('plugin-autocomplete',  'sdkjs-plugins/plugin-autocomplete'))
    repos.add(getRepoMap('plugin-easybib',       'sdkjs-plugins/plugin-easybib'))
    repos.add(getRepoMap('plugin-wordpress',     'sdkjs-plugins/plugin-wordpress'))
    repos.add(getRepoMap('plugin-zotero',        'sdkjs-plugins/plugin-zotero'))
    repos.add(getRepoMap('plugin-mendeley',      'sdkjs-plugins/plugin-mendeley'))
    repos.add(getRepoMap('sdkjs'))
    repos.add(getRepoMap('sdkjs-comparison'))
    repos.add(getRepoMap('sdkjs-content-controls'))
    repos.add(getRepoMap('sdkjs-disable-features'))
    repos.add(getRepoMap('server'))
    repos.add(getRepoMap('server-license'))
    repos.add(getRepoMap('server-lockstorage'))
    repos.add(getRepoMap('web-apps'))
    repos.add(getRepoMap('web-apps-mobile'))
    repos.add(getRepoMap('Docker-DocumentServer'))
    repos.add(getRepoMap('DocumentBuilder'))
    return repos
}

def checkoutRepos(String branch = 'master')
{    
    for (repo in getReposList()) {
        checkoutRepo(repo.name, branch, repo.dir, repo.owner)
    }

    return this
}

def tagRepos(String tag)
{
    for (repo in getReposList()) {
        sh "cd ${repo.dir} && \
            git tag -l | xargs git tag -d && \
            git fetch --tags && \
            git tag ${tag} && \
	        git push origin --tags"
    }

    return this
}

def protectRelease(String branch)
{
    for (repo in getReposList()) {
        sh """cd ${repo.dir}
            echo '{
                "required_status_checks": null,
                "enforce_admins": true,
                "required_pull_request_reviews": null,
                "restrictions": {
                    "users": [],
                    "teams": [
                        "dep-application-development-admins"
                    ]
                }
            }' | \
            hub api -X PUT \
                repos/${repo.owner}/${repo.name}/branches/${branch}/protection \
                --input -
        """
    }
    return this
}

def unprotectRelease(String branch)
{
    for (repo in getReposList()) {
        sh """cd ${repo.dir}
            hub api -X DELETE \
                repos/${repo.owner}/${repo.name}/branches/${branch}/protection || \
            true
        """
    }
    return this
}

def getConfParams(String platform, Boolean clean, String license)
{
    def modules = []
    if (license == "opensource" && params.core) {
        modules.add('core')
    }
    if (license == "freemium" && params.desktopeditor) {
        modules.add('desktop')
    }
    if (license == "opensource" && params.documentbuilder) {
        modules.add('builder')
    }
    if ((license == "opensource"
        && params.documentserver)
        || (license == "commercial"
        && (params.documentserver_ie
        || params.documentserver_de))) {
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
    if (license == "commercial" || license == "freemium") {
        confParams.add("--sdkjs-addon comparison")
        confParams.add("--sdkjs-addon content-controls")
        confParams.add("--server-addon license")
        confParams.add("--server-addon lockstorage")
        confParams.add("--web-apps-addon mobile")
    }
    if (license == "freemium") {
        confParams.add("--sdkjs-addon-desktop disable-features")
    }
    if (params.extra_params) {
        confParams.add(params.extra_params)
    }

    return confParams.join(' ')
}

def linuxBuild(String platform = 'native', Boolean clean = true, String license = 'opensource')
{
    sh "cd build_tools && \
        ./configure.py ${getConfParams(platform, clean, license)} &&\
        ./make.py"

    return this
}

def linuxBuildDesktop(String platform = 'native')
{
    sh "cd desktop-apps/win-linux/package/linux &&\
         make clean &&\
         make deploy"

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
         make deploy"

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
        make deploy"

    sh "cd Docker-DocumentServer && \
        export PRODUCT_NAME=${productName} && \
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
    checkoutRepo('doc-builder-testing', 'master')
    sh "docker rmi doc-builder-testing || true"
    sh "cd doc-builder-testing &&\
        docker build --tag doc-builder-testing -f dockerfiles/debian-develop/Dockerfile . &&\
        docker run --rm doc-builder-testing bundle exec parallel_rspec spec -n 2"

    return this
}

def windowsBuild(String platform = 'native', Boolean clean = true, String license = 'opensource')
{
    bat "cd build_tools &&\
            call python configure.py ${getConfParams(platform, clean, license)} &&\
            call python make.py"

    return this
}

def windowsBuildDesktop (String platform)
{
    bat "cd desktop-apps &&\
            mingw32-make clean-package &&\
            mingw32-make deploy"

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
        mingw32-make deploy"

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
        mingw32-make deploy"

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
