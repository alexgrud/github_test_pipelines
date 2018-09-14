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
def manila_enabled
node{
    stage ("1") {
      manila_enabled = "True".toBoolean()
    }
        stage ("2") {
            if (manila_enabled){
              println("test passed")
            }
          }
        stage ("3") {
          }
}
