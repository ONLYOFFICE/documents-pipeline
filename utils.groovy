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
    repos.add(getRepoMap('documents-pipeline'))
    repos.add(getRepoMap('documents-pipeline', 'r7-documents-pipeline', 'ASC-OFFICE'))
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
    repos.add(getRepoMap('r7', 'r7', 'ASC-OFFICE'))
    repos.add(getRepoMap('sdkjs'))
    repos.add(getRepoMap('sdkjs-comparison'))
    repos.add(getRepoMap('sdkjs-content-controls'))
    repos.add(getRepoMap('sdkjs-disable-features'))
    repos.add(getRepoMap('sdkjs-sheet-views'))
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

def printBranches(String branch, Map repo)
{
    return sh (
        label: "${repo.owner}/${repo.name}: branches",
        script: """
            gh api -X GET repos/${repo.owner}/${repo.name}/branches?per_page=100 | \
            jq -c '.[] | { name, protected }'
        """,
        returnStatus: true
    )
}

def protectBranch(String branch, Map repo)
{
    return sh (
        label: "${repo.owner}/${repo.name}: protect ${branch}",
        script: """
            echo '{
                "required_status_checks": null,
                "enforce_admins": true,
                "required_pull_request_reviews": null,
                "restrictions": {
                    "users": [],
                    "teams": [
                        "dep-application-development-leads"
                    ]
                }
            }' | \
            gh api -X PUT \
                repos/${repo.owner}/${repo.name}/branches/${branch}/protection \
                --input -
        """,
        returnStatus: true
    )
}

def unprotectBranch(String branch, Map repo)
{
    return sh (
        label: "${repo.owner}/${repo.name}: unprotect ${branch}",
        script: """
            gh api -X DELETE \
                repos/${repo.owner}/${repo.name}/branches/${branch}/protection
        """,
        returnStatus: true
    )
}

def createBranch(String branch, String baseBranch, Map repo)
{
    return sh (
        label: "${repo.owner}/${repo.name}: start ${branch}",
        script: """
            if [ \$(git branch -a | grep 'develop' | wc -c) -eq 0 ]; then
                git checkout -f master
                git checkout -b develop
                git push origin develop
            fi
            if [ \$(git branch -a | grep '${branch}' | wc -c) -ne 0 ]; then
                exit 0
            fi
            git checkout -f ${baseBranch}
            git pull --ff-only origin ${baseBranch}
            git checkout -B ${branch}
            git push origin ${branch}
        """,
        returnStatus: true
    )
}

def mergeBranch(String branch, ArrayList baseBranches, Map repo)
{
    return sh (
        label: "${repo.owner}/${repo.name}: merge ${branch} to ${baseBranches.join(', ')}",
        script: """#!/bin/bash -xe
            if [ \$(git branch -a | grep '${branch}' | wc -c) -eq 0 ]; then
                exit 0
            fi
            base_branches=(${baseBranches.join(' ')})
            merge=0
            for base in \${base_branches[*]}; do
                git checkout -f ${branch}
                git pull --ff-only origin ${branch}
                gh pr create \
                    --base \$base \
                    --title \"Merge branch ${branch} into \$base\" \
                    --body \"\" || \
                true
                git checkout \$base
                git pull --ff-only origin \$base
                git merge ${branch} \
                    --no-edit --no-ff \
                    -m \"Merge branch ${branch} into \$base\" || \
                continue
                git push origin \$base
                ((++merge))
            done
            if [ \$merge -ne \${#base_branches[@]} ]; then
                exit 2
            fi
        """,
        returnStatus: true
    )
}

def deleteBranch(String branch, Map repo)
{
    return sh (
        label: "${repo.owner}/${repo.name}: delete ${branch}",
        script: """
            if [ \$(git branch -a | grep '${branch}' | wc -c) -eq 0 ]; then
                exit 0
            fi
            git branch -D ${branch}
            git push --delete origin ${branch}
        """,
        returnStatus: true
    )
}

def setBuildStatus(Integer success, Integer total)
{
    if (success == 0) {
        currentBuild.result = "FAILURE"
    } else if (success != total) {
        currentBuild.result = "UNSTABLE"
    } else if (success == total) {
        currentBuild.result = "SUCCESS"
    }
    return this
}

def printReposBranches()
{
    def success = 0
    def total = getReposList().size()
    for (repo in getReposList()) {
        def ret = printBranches(branch, repo)
        if (ret == 0) { success++ }
    }
    setBuildStatus(success, total)
    return this
}

def protectRelease(String branch)
{
    def success = 0
    def total = getReposList().size()
    for (repo in getReposList()) {
        def ret = protectBranch(branch, repo)
        if (ret == 0) { success++ }
    }
    setBuildStatus(success, total)
    return this
}

def unprotectRelease(String branch)
{
    def success = 0
    def total = getReposList().size()
    for (repo in getReposList()) {
        def ret = unprotectBranch(branch, repo)
        if (ret == 0) { success++ }
    }
    setBuildStatus(success, total)
    return this
}

def startRelease(String branch, String baseBranch, Boolean protect)
{
    def success = 0
    def total = getReposList().size()
    for (repo in getReposList()) {
        dir (repo.dir) {
            def retC = createBranch(branch, baseBranch, repo)
            if (protect) {
                def retP = protectBranch(branch, repo)
                if (retC == 0 && retP == 0) { success++ }
            } else {
                if (retC == 0) { success++ }
            }
        }
    }
    setBuildStatus(success, total)
    return this
}

def mergeRelease(String branch)
{
    def success = 0
    def total = getReposList().size()
    def baseBranches = ['master']
    for (repo in getReposList()) {
        dir (repo.dir) {
            def retM = mergeBranch(branch, baseBranches, repo)
            if (retM == 0) { success++ }
        }
    }
    setBuildStatus(success, total)
    return this
}

def finishRelease(String branch, String extraBranch = '')
{
    def success = 0
    def total = getReposList().size()
    def baseBranches = ['master', 'develop']
    if (!extraBranch.isEmpty()) {
        baseBranches.add(extraBranch)
    }
    for (repo in getReposList()) {
        dir (repo.dir) {
            def retM = mergeBranch(branch, baseBranches, repo)
            if (retM == 0) {
                def retU = unprotectBranch(branch, repo)
                def retD = deleteBranch(branch, repo)
                if (retD == 0) { success++ }
            }
        }
    }
    setBuildStatus(success, total)
    return this
}

def getConfParams(String platform, Boolean clean, String license)
{
    def modules = []
    if (license == "opensource") {
        if(params.core) {
            modules.add('core')
        }
        // Add module to build to enforce clean it on build
        if(params.desktopeditor && clean) {
            modules.add('desktop')
        }
        if (params.documentbuilder) {
            modules.add('builder')
        }
    }
    if (license == "commercial" && params.desktopeditor) {
        modules.add('desktop')
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
    confParams.add("--branding r7")
    confParams.add("--branding-name r7-office")
    if (!params.extra_params.isEmpty()) {
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
