// Expected parameters:
//   SALT_MASTER_HOST
common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
orchestrate = new com.mirantis.mk.Orchestrate()
salt = new com.mirantis.mk.Salt()
def venv
def venvPepper
def outputs = [:]
def extra_tgt = ""
node{
    def artifacts_dir = '_artifacts/'
    stage ("Preparing data") {
        def workspace = common.getWorkspace()
        venv = "${workspace}/venv"
        venvPepper = "${workspace}/venvPepper"
        println("Test message")
        println(venvPepper)
        saltMasterHost=SALT_MASTER_HOST
        SALT_MASTER_URL = "http://${saltMasterHost}:6969"
        println(SALT_MASTER_URL)
        SALT_MASTER_CREDENTIALS="salt-qa-credentials"
        println(SALT_MASTER_CREDENTIALS)
    }
        stage ("installing virtualenv Pepper") {
          // Setup virtualenv for pepper
          python.setupPepperVirtualenv(venvPepper, SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
          }
        stage ("Running certain stages") {
          //salt.enforceState(venvPepper, 'I@keystone:server:role:primary', 'keystone.upgrade.pre')
          sh "mkdir -p ${artifacts_dir}"
          def myMap = [:]
          myMap['queens'] = [:]
          myMap['queens']['keystone'] = 'queens-xenial-20190426084537'
          myMap['queens']['neutron'] = 'queens-xenial-20190426084537'
          //sh "rm ${artifacts_dir}/queens.txt"
          writeYaml file: "${artifacts_dir}/queens.txt", data: myMap
          }
        stage('Archive artifacts'){
          archiveArtifacts allowEmptyArchive: true, artifacts: "${artifacts_dir}/*", excludes: null
        }
        stage('Cleanup') {
          deleteDir()
        }
}
