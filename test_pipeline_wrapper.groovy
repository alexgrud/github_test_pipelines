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
    def testOutputDir = sh(script: 'mktemp -d', returnStdout: true).trim()
    def resultMap = [:]
    stage ("Running run_salt_certain_stage job") {
      try {
      deployBuild = build(job: certainStageJob, propagate: false, parameters: [
        [$class: 'StringParameterValue', name: 'SALT_MASTER_HOST', value: SALT_MASTER_HOST],
      ])
      buildJobNumber=deployBuild.getNumber()
      stage('Get artifacts') {
            def selector = [$class: 'SpecificBuildSelector', buildNumber: "${buildJobNumber}"]
            step ([$class: 'CopyArtifact',
                   projectName: certainStageJob,
                   selector: selector,
                   filter: '_artifacts/*.yml',
                   target: testOutputDir,
                   flatten: true,])
        }
        def files = sh(script: "find ${testOutputDir} -name *.yml", returnStdout: true).readLines()
        println(files)
        
        for (file in files) {
            println(file)
            def data = readYaml file: file
            resultMap<<data
        }
        println(resultMap)
      } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
      }
    }
}

