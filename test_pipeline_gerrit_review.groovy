/**
Expected parameters:
CREDENTIALS_ID
GERRIT_SCHEME
GERRIT_HOST
GERRIT_PORT
METADATA_PROJECT_NAMESPACE
METADATA_PROJECT_NAME
GERRIT_BRANCH
*/

import groovy.json.JsonSlurper

common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
orchestrate = new com.mirantis.mk.Orchestrate()
salt = new com.mirantis.mk.Salt()
//to change!!!!!
gerrit = new  com.mirantis.mk.Gerrit_test()

if (!env.GERRIT_SCHEME || !env.GERRIT_HOST || !env.GERRIT_PORT) {
    GERRIT_SCHEME = 'ssh'
    GERRIT_HOST = 'gerrit.mcp.mirantis.net'
    GERRIT_PORT = '29418'
}

//temporary - to move to expected parameters
METADATA_PROJECT_NAMESPACE = "mcp"
METADATA_PROJECT_NAME = "release-metadata"
GERRIT_BRANCH = "master"
CHANGE_AUTHOR_NAME = "MCP-CI"
CHANGE_AUTHOR_EMAIL = "mcp-ci-jenkins@ci.mcp.mirantis.net"


def cred = common.getCredentials(CREDENTIALS_ID, 'key')
String GERRIT_USER = cred.username
String gerritUrl = "${GERRIT_SCHEME}://${GERRIT_USER}@${GERRIT_HOST}:${GERRIT_PORT}"
//println(gerritUrl)
node{
    def workspace = common.getWorkspace()
    def venvDir = "${workspace}/gitreview-venv"
    def repoDir = "${venvDir}/repo"
    def metadataDir = "${repoDir}/metadata"
    //temporary stuff for testing
    def resultBuiltImages = [:]
    def details = "https://ci.mcp.mirantis.net/view/Openstack%20Images/job/loci-build-openstack-all-release-images/2/artifact/_artifacts/stein.yml/"
    def data = readYaml text: details.toURL().getText()
    resultBuiltImages<<data
    println(resultBuiltImages)
    def specChangeID
    def gitOpts
    def commitMessage
    def gitRemote

    //end of temporary stuff
    

    stage ("Installing virtualenv") {
        python.setupVirtualenv(venvDir, 'python3', ['git-review', 'PyYaml'])
    }
    stage('Cloning release-metadata repository') {
        dir(repoDir) {
            deleteDir()
            git([
                url: "${gerritUrl}/mcp/release-metadata",
                branch: 'master',
                credentialsId: CREDENTIALS_ID,
                poll: true,
            ])
            //def files = sh(script: "find ${repoDir} *", returnStdout: true).readLines()
            //println(files)
        gitRemote = sh(
                script:
                    'git remote -v | head -n1 | cut -f1',
                returnStdout: true,
            ).trim()
        }

    }
    
    stage('Creating CRs') {
    for (openstackRelease in resultBuiltImages.keySet()) {
        def crTopic = "nightly_update_images_" + openstackRelease
        println(crTopic)
        //Check if CR already exist

        def gerritAuth=['PORT':'29418', 'USER':GERRIT_USER, 'HOST':GERRIT_HOST]
        def changeParams=['owner':GERRIT_USER, 'status':'open', 'project':"${METADATA_PROJECT_NAMESPACE}/${METADATA_PROJECT_NAME}", 'branch':GERRIT_BRANCH, 'topic':crTopic]
        def jsonChange = gerrit.findGerritChange(CREDENTIALS_ID, gerritAuth, changeParams)

        println(jsonChange)
        if ( jsonChange ) {
            def jsonSlurper = new JsonSlurper()
            changeNum = jsonSlurper.parseText(jsonChange)['number'].toString()
            jsonSlurper = null
            println(changeNum)
            
            //get existent change from gerrit
            dir(repoDir) {
            sh """#!/bin/bash -ex
                        source ${venvDir}/bin/activate
                        git review -d ${changeNum}
                    """
            }
        }

        for (component in resultBuiltImages[openstackRelease].keySet()) {

            resultBuiltImages[openstackRelease][component].each {
                println(it.key+" "+it.value)
                python.runVirtualenvCommand(venvDir, "python ${repoDir}/utils/app.py --path ${metadataDir} update --key images:openstack:${openstackRelease}:${component}:${it.key} --value ${it.value}")
            }
        }
        

       
        //for testing
        dir(repoDir) {
        sh """#!/bin/bash -ex
          source ${venvDir}/bin/activate
          git branch
          git status
          """
        }


        if ( jsonChange ) {
            specChangeID = sh (
            script: 'git --no-pager log -1 | egrep -o "Change-Id:.*" | tail -1',
            returnStdout: true,
            ).trim()
        } else {
            specChangeID = ''
        }
        commitMessage =
            """[oscore] Auto-update ${METADATA_PROJECT_NAME} 

               |${specChangeID}
            """.stripMargin()

        if ( jsonChange ) {
            gitOpts = "--amend --message \"${commitMessage}\""
        } else {
            gitOpts = "--message \"${commitMessage}\""
        }



        dir(repoDir) {
            sh """
               git add ${metadataDir}
            """

            sh """
                GIT_COMMITTER_NAME=${CHANGE_AUTHOR_NAME} \
                GIT_COMMITTER_EMAIL=${CHANGE_AUTHOR_EMAIL} \
                git commit ${gitOpts} --author '${CHANGE_AUTHOR_NAME} \
                    <${CHANGE_AUTHOR_EMAIL}>'
            """

            sshagent([CREDENTIALS_ID]) {
                    reviewOut = sh(
                        script:
                            """#!/bin/bash -ex
                                source ${venvDir}/bin/activate
                                GIT_COMMITTER_NAME=${CHANGE_AUTHOR_NAME} \
                                GIT_COMMITTER_EMAIL=${CHANGE_AUTHOR_EMAIL} \
                                git review -r ${gitRemote} \
                                    -t ${crTopic} \
                                    ${GERRIT_BRANCH}
                            """,
                        returnStdout: true,
                    ).trim()
            }
        }




    }


    }
   
}
