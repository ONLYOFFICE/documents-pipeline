def checkoutRepo(String repo, String branch = 'master', String company = 'ONLYOFFICE') {
    checkout([
            $class: 'GitSCM',
            branches: [[
                    name: branch
                ]
            ],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[
                    $class: 'RelativeTargetDirectory',
                    relativeTargetDir: repo
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
    repos.add('sdkjs')
    repos.add('sdkjs-comparison')
    repos.add('sdkjs-content-controls')
    repos.add('sdkjs-plugins')
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
        if( repo != 'r7' ) {
            checkoutRepo(repo, branch)
        }
        else {
            checkoutRepo(repo, branch, 'ASC-OFFICE')
        }
    }

    return this
}

def tagRepos(String tag)
{
    for (repo in getReposList()) {
        sh "cd ${repo} && \
            git tag -l | xargs git tag -d && \
            git fetch --tags && \
            git tag ${tag} && \
	        git push origin --tags"
    }

    return this
}
def linuxBuild(String platform = 'native', Boolean clean = true, Boolean noneFree = false)
{
    String confParams = "\
        --module \"desktop builder core server\"\
        --platform ${platform}\
        --update false\
        --branding r7\
        --branding-name r7-office\
        --clean ${clean.toString()}\
        --qt-dir \$QT_PATH"

    if (noneFree) {
        confParams = confParams.concat(" --sdkjs-addon comparison")
        confParams = confParams.concat(" --sdkjs-addon content-controls")
        confParams = confParams.concat(" --server-addon license")
        confParams = confParams.concat(" --server-addon lockstorage")
        confParams = confParams.concat(" --web-apps-addon mobile")
    }

    sh "cd build_tools && \
        ./configure.py ${confParams} &&\
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

def linuxBuildServer(String productName='documentserver')
{
    sh "cd document-server-package && \
        export PRODUCT_NAME=${productName} && \
        make clean && \
        make deploy"

    sh "cd Docker-DocumentServer && \
        export PRODUCT_NAME=${productName} && \
        export ONLYOFFICE_VALUE=ds && \
        make clean && \
        make deploy"

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
    checkoutRepo('doc-builder-testing')
    sh "docker rmi doc-builder-testing || true"
    sh "cd doc-builder-testing &&\
        docker build --tag doc-builder-testing -f dockerfiles/debian-develop/Dockerfile . &&\
        docker run --rm doc-builder-testing bundle exec parallel_rspec spec -n 2"

    return this
}

def windowsBuild(String platform = 'native', Boolean clean = true, Boolean noneFree = false)
{
    String confParams = "\
        --module \"desktop builder core tests updmodule server\"\
        --platform ${platform}\
        --update false\
        --branding r7\
        --branding-name R7-Office\
        --clean ${clean.toString()}\
        --qt-dir %QT_PATH%\
        --qt-dir-xp %QT56_PATH%"

    if (noneFree) {
        confParams = confParams.concat(" --sdkjs-addon comparison")
        confParams = confParams.concat(" --sdkjs-addon content-controls")
        confParams = confParams.concat(" --server-addon license")
        confParams = confParams.concat(" --server-addon lockstorage")
        confParams = confParams.concat(" --web-apps-addon mobile")
    }

    bat "cd build_tools &&\
            call python configure.py ${confParams} &&\
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

def windowsBuildServer(String productName='DocumentServer')
{
    bat "cd document-server-package && \
        set \"PRODUCT_NAME=${productName}\" && \
        mingw32-make clean && \
        mingw32-make deploy"

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
