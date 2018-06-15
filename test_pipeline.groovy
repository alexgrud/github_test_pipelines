common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
orchestrate = new com.mirantis.mk.Orchestrate()
salt = new com.mirantis.mk.Salt()
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
	saltMasterHost="172.17.49.168"
	SALT_MASTER_URL = "http://${saltMasterHost}:6969"
	println(SALT_MASTER_URL)
	SALT_MASTER_CREDENTIALS="salt-qa-credentials"
	println(SALT_MASTER_CREDENTIALS)
	}
	stage ("installing virtualenv Pepper") {
		// Setup virtualenv for pepper
        	python.setupPepperVirtualenv(venvPepper, SALT_MASTER_URL, SALT_MASTER_CREDENTIALS)
		}
	stage ("Running salt state") {
		orchestrate.RunTestSaltCmd(venvPepper)
		}
}
