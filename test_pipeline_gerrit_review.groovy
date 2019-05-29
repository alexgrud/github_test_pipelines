/**
Expected parameters:
CREDENTIALS_ID
GERRIT_SCHEME
GERRIT_HOST
GERRIT_PORT
*/
common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
orchestrate = new com.mirantis.mk.Orchestrate()
salt = new com.mirantis.mk.Salt()
if (!env.GERRIT_SCHEME || !env.GERRIT_HOST || !env.GERRIT_PORT) {
    GERRIT_SCHEME = 'ssh'
    GERRIT_HOST = 'gerrit.mcp.mirantis.net'
    GERRIT_PORT = '29418'
}
def cred = common.getCredentials(CREDENTIALS_ID, 'key')
String GERRIT_USER = cred.username
String gerritUrl = "${GERRIT_SCHEME}://${GERRIT_USER}@${GERRIT_HOST}:${GERRIT_PORT}"
println(gerritUrl)
node{
    venvDir = "${env.HOME}/gitreview-venv"
    metadataDir = "${venvDir}/metadata"
    stage ("Preparing directory") {
        sh """#!/bin/bash -ex
            rm -fr ${venvDir}
            virtualenv ${venvDir}
        """
        dir(metadataDir) {
            deleteDir()
            git([
                url: "${gerritUrl}/mcp/release-metadata",
                branch: 'master',
                credentialsId: CREDENTIALS_ID,
                poll: true,
            ])
            def files = sh(script: "find ${metadataDir} *", returnStdout: true).readLines()
            println(files)
        }

    }
}
