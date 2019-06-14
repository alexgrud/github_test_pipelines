/**
Expected parameters:
REPO_URL
REPO_BRANCH
KUBECTL_VERSION
CLUSTER_NAME
NAMESPACE
*/
common = new com.mirantis.mk.Common()

//temporary for testing
REPO_URL = 'https://github.com/alexgrud/kaas.git'
REPO_BRANCH = 'master'
KUBECTL_VERSION='v1.14.0'
CLUSTER_NAME = 'ogrudev-test02'
NAMESPACE = 'imc-oscore-team'
//end temporary

def runKubectlCmd(kubectlDir, cmd) {
    def common = new com.mirantis.mk.Common()
    cmdRes = sh(script: "${kubectlDir}/kubectl ${cmd}", returnStdout: true).trim()
    return cmdRes
}
node{
	def workspace = common.getWorkspace()
	def repoDir = "${workspace}/repo"

	stage('Cleanup') {
        deleteDir()
    }

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

    stage('Installing kubectl') {
    	if (common.shCmdStatus("curl -LO https://storage.googleapis.com/kubernetes-release/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl; chmod +x ${workspace}/kubectl")['status' != 0]) {
    		common.errorMsg("Failed to download kubectl")
    	}
    }

    stage('Prepare contexts') {
    	contextCluster = readYaml file: "${repoDir}/context_cluster.yml"
    	contextMachines = readYaml file: "${repoDir}/context_machines_1_controller_2_nodes.yml"
    	println(contextCluster)
    	contextCluster['metadata']['name'] = CLUSTER_NAME
    	contextCluster['metadata']['namespace'] = NAMESPACE
    	writeYaml file: "${workspace}/context_cluster.yml", data: contextCluster
    	//create kaas cluster
    	runKubectlCmd(workspace, "--kubeconfig=${repoDir}/kubeconfig-kaas.yml apply -f ${workspace}/context_cluster.yml")
    	println(contextMachines)
    	def contextMachinesUpdated = ['apiVersion': 'v1', 'kind': 'List', 'metadata':['resourceVersion':'', 'selfLink':''], 'items':[]]
    	for (node in contextMachines['items']) {
    		def nodeNameTemplate = node['metadata']['name']
    		node['metadata']['generateName'] = "${CLUSTER_NAME}-"
    		node['metadata']['labels']['cluster.k8s.io/cluster-name'] = "${CLUSTER_NAME}"
    		node['metadata']['name'] = "${CLUSTER_NAME}-${nodeNameTemplate}"
    		contextMachinesUpdated['items'] << node
    	}
    	println(contextMachinesUpdated)
    	writeYaml file: "${workspace}/context_machines.yml", data: contextMachinesUpdated
    	runKubectlCmd(workspace, "--kubeconfig=${repoDir}/kubeconfig-kaas.yml apply -f ${workspace}/context_machines.yml")
    	
    }

    stage('Cleanup') {
        deleteDir()
    }
}
