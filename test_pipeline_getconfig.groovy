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
          def _orch = salt.getConfig(venvPepper, "I@salt:master ${extra_tgt}", "orchestration.deploy.applications")
          if ( !_orch['return'][0].values()[0].isEmpty() ) {
          Map<String,Integer> _orch_app = [:]
            for (k in _orch['return'][0].values()[0].keySet()) {
              _orch_app[k] = _orch['return'][0].values()[0][k].values()[0].toInteger()
            }
          def _orch_app_sorted = common.SortMapByValueAsc(_orch_app)
          println(_orch_app_sorted.keySet())
          def out = orchestrate.OrchestrateApplications(venvPepper, "I@salt:master ${extra_tgt}", _orch_app_sorted.keySet())
          }
          }
         
}