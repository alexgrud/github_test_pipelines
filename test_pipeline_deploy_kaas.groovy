/**
Expected parameters:
REPO_URL
REPO_BRANCH
*/
common = new com.mirantis.mk.Common()

//temporary for testing
REPO_URL = 'https://github.com/alexgrud/kaas.git'
REPO_BRANCH = 'master'
//end temporary

node{
    def workspace = common.getWorkspace()
    def repoDir = "${workspace}/repo"

    stage('Cloning repository') {
        dir(repoDir) {
            deleteDir()
            git([
                url: REPO_URL,
                branch: REPO_BRANCH,
             //   credentialsId: CREDENTIALS_ID,
                poll: true,
            ])
        }
    }


}
