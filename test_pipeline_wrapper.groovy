/**
Expected parameters:
SALT_MASTER_HOST
*/
common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
orchestrate = new com.mirantis.mk.Orchestrate()
salt = new com.mirantis.mk.Salt()
node{
    def artifacts_dir = '_artifacts/'
    def certainStageJob = "run_salt_certain_stage"
    stage ("Running run_salt_certain_stage job") {
      try {
      deployBuild = build(job: certainStageJob, propagate: false, parameters: [
        [$class: 'StringParameterValue', name: 'SALT_MASTER_HOST', value: SALT_MASTER_HOST],
      ])
      } catch (Exception e) {
        currentBuild.result = 'FAILURE'
      }
    }
}
