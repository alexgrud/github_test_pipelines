// Expected parameters:
//   SALT_MASTER_HOST
common = new com.mirantis.mk.Common_test()
python = new com.mirantis.mk.Python()
orchestrate = new com.mirantis.mk.Orchestrate_test()
salt = new com.mirantis.mk.Salt_test()
def venv
def venvPepper
def outputs = [:]
node{
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
        stage ("Running get config") {
          def _orch = salt.getConfig(venvPepper, 'I@salt:master', 'orchestration.deploy.applications')
          if ( !_orch['return'].isEmpty() ) {
          Map<String,Integer> _orch_app = [:]
          //println(_orch_app['cinder'])
          for (k in _orch['return'][0].values()[0].keySet()) {
              _orch_app[k] = _orch['return'][0].values()[0][k].values()[0].toInteger()
          }
          def _orch_app_sorted = common.SortMapByValueAsc(_orch_app)
          println(_orch_app_sorted.keySet())
          def out = orchestrate.OrchestrateOpenstackApplications(venvPepper, 'I@salt:master', _orch_app_sorted.keySet())
          }
          }
}