/**
Expected parameters:
CONTEXT_REPO_URL
CONTEXT_REPO_BRANCH
OSH_REPO_URL
OSH_REPO_BRANCH
CREDENTIALS_ID
CLUSTER_NAME
NAMESPACE
KAAS_USER
CLUSTER_CONTEXT_NAME
MACHINES_CONTEXT_NAME
OPENSTACK_CONTEXT_NAME
*/
common = new com.mirantis.mk.Common()
git = new com.mirantis.mk.Git()

//temporary for testing
CONTEXT_REPO_URL = 'https://github.com/alexgrud/kaas.git'
CONTEXT_REPO_BRANCH = 'master'
CLUSTER_NAME = 'ogrudev7'
NAMESPACE = 'imc-oscore-team'
KAAS_USER = 'ogrudev'
CLUSTER_CONTEXT_NAME = 'context_cluster'
MACHINES_CONTEXT_NAME = "context_machines_1_controller_2_nodes"
OSH_REPO_URL = 'ssh://mcp-ci-gerrit@gerrit.mcp.mirantis.net:29418/mcp/osh-operator'
OSH_REPO_BRANCH = 'master'
CREDENTIALS_ID = "mcp-ci-gerrit"
OPENSTACK_CONTEXT_NAME = 'core-ceph'
//end temporary

def runKubectlCmd(kubectlDir, cmd) {
    def common = new com.mirantis.mk.Common()
    cmdRes = sh(script: "${kubectlDir}/kubectl ${cmd}", returnStdout: true).trim()
    return cmdRes
}
node{
    def workspace = common.getWorkspace()
    def contextRepoDir = "${workspace}/context_repo"
    def oshRepoDir = "${workspace}/osh_repo"
    def kubectlVersion='v1.14.0'
    def nodesDetails = [:]
    def clusterNodesRequested = ''
    def clusterNodesReady = ''
    def retries = 20
    def delay = 120
    def clusterCreated = false
    def clusterLbHost = ''
    def clusterApiCert = ''
    def kubeconfigNewCluster = [:]
    def kubeconfigKaasName = 'kubeconfig-kaas'
    def kubeconfigNewClusterName = 'kubeconfig-new-cluster'

    stage('Cleanup') {
        deleteDir()
    }

    stage('Cloning repository with contexts') {
        dir(contextRepoDir) {
            deleteDir()
            git([
                url: CONTEXT_REPO_URL,
                branch: CONTEXT_REPO_BRANCH,
                credentialsId: CREDENTIALS_ID,
                poll: true,
            ])
        }
    }

    stage('Installing kubectl') {
        if (common.shCmdStatus("curl -LO https://storage.googleapis.com/kubernetes-release/release/${kubectlVersion}/bin/linux/amd64/kubectl; chmod +x ${workspace}/kubectl")['status' != 0]) {
            common.errorMsg("Failed to download kubectl")
        }
    }

    stage('Prepare contexts') {
        contextCluster = readYaml file: "${contextRepoDir}/${CLUSTER_CONTEXT_NAME}.yml"
        contextMachines = readYaml file: "${contextRepoDir}/${MACHINES_CONTEXT_NAME}.yml"
        contextCluster['metadata']['name'] = CLUSTER_NAME
        contextCluster['metadata']['namespace'] = NAMESPACE
        writeYaml file: "${workspace}/${CLUSTER_CONTEXT_NAME}.yml", data: contextCluster
        //create kaas cluster
        runKubectlCmd(workspace, "--kubeconfig=${contextRepoDir}/${kubeconfigKaasName}.yml apply -f ${workspace}/${CLUSTER_CONTEXT_NAME}.yml")
        def contextMachinesUpdated = ['apiVersion': 'v1', 'kind': 'List', 'metadata':['resourceVersion':'', 'selfLink':''], 'items':[]]
        for (node in contextMachines['items']) {
            def nodeNameTemplate = node['metadata']['name']
            node['metadata']['generateName'] = "${CLUSTER_NAME}-"
            node['metadata']['labels']['cluster.k8s.io/cluster-name'] = "${CLUSTER_NAME}"
            node['metadata']['name'] = "${CLUSTER_NAME}-${nodeNameTemplate}"
            nodesDetails["${CLUSTER_NAME}-${nodeNameTemplate}"] = [:]
            contextMachinesUpdated['items'] << node
        }
        writeYaml file: "${workspace}/${MACHINES_CONTEXT_NAME}.yml", data: contextMachinesUpdated
        runKubectlCmd(workspace, "--kubeconfig=${contextRepoDir}/${kubeconfigKaasName}.yml apply -f ${workspace}/${MACHINES_CONTEXT_NAME}.yml")
    }

    stage('Waiting for cluster to become ready') {
        int retry = 0
        while (retry++ < retries) {
            common.infoMsg("Waiting for all cluster nodes to become ready. Retry ${retry} out of ${retries}")
            clusterNodesReady = runKubectlCmd(workspace, "--kubeconfig=${contextRepoDir}/${kubeconfigKaasName}.yml get cluster.cluster.k8s.io/${CLUSTER_NAME} -n ${NAMESPACE} -o=jsonpath='{.status.providerStatus.nodes.ready}'")
            clusterNodesRequested = runKubectlCmd(workspace, "--kubeconfig=${contextRepoDir}/${kubeconfigKaasName}.yml get cluster.cluster.k8s.io/${CLUSTER_NAME} -n ${NAMESPACE} -o=jsonpath='{.status.providerStatus.nodes.requested}'")
            if (clusterNodesRequested !='' && clusterNodesReady !='') {
                if (clusterNodesRequested == clusterNodesReady) {
                    clusterCreated = true
                    break;
                } else {
                    sleep(delay)
                }
            } else {
                sleep(delay)
            }
        }
        if (clusterCreated) {
            common.infoMsg("Cluster and nodes have been created successfully")
        } else {
            throw new RuntimeException("Cluster node(s) failed to become ready after ${retry} retries. Nodes requested: ${clusterNodesRequested}, nodes ready: ${clusterNodesReady}")
        }

    }

    stage('Preparing kubeconfig for new cluster') {
        //TODO(ogrudev) in the future need to download kubeconfig file using UI/CLI hence we won`t need to change template file
        clusterLbHost = runKubectlCmd(workspace, "--kubeconfig=${contextRepoDir}/${kubeconfigKaasName}.yml get cluster.cluster.k8s.io/${CLUSTER_NAME} -n ${NAMESPACE} -o=jsonpath='{.status.providerStatus.loadBalancerHost}'")
        clusterApiCert = runKubectlCmd(workspace, "--kubeconfig=${contextRepoDir}/${kubeconfigKaasName}.yml get cluster.cluster.k8s.io/${CLUSTER_NAME} -n ${NAMESPACE} -o=jsonpath='{.status.providerStatus.apiServerCertificate}'")
        if (clusterLbHost == '' || clusterApiCert == '') {
            throw new RuntimeException("Unable to get cluster details")
        }
        kubeconfigNewCluster = readYaml file: "${contextRepoDir}/${kubeconfigNewClusterName}.yml"
        kubeconfigNewCluster['clusters'][0]['name'] = CLUSTER_NAME
        kubeconfigNewCluster['clusters'][0]['cluster']['certificate-authority-data'] = clusterApiCert
        kubeconfigNewCluster['clusters'][0]['cluster']['server'] = "https://${clusterLbHost}:443"
        kubeconfigNewCluster['contexts'][0]['name'] = "${KAAS_USER}@${CLUSTER_NAME}"
        kubeconfigNewCluster['contexts'][0]['context']['cluster'] = CLUSTER_NAME
        kubeconfigNewCluster['contexts'][0]['context']['user'] = KAAS_USER
        kubeconfigNewCluster['current-context'] = "${KAAS_USER}@${CLUSTER_NAME}"

        writeYaml file: "${workspace}/${kubeconfigNewClusterName}.yml", data: kubeconfigNewCluster

    }

    //test
    println(runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml get nodes"))

    stage('Cloning OSH operator repository') {
        git.checkoutGitRepository(oshRepoDir, OSH_REPO_URL, OSH_REPO_BRANCH, CREDENTIALS_ID, true, 10, 0)
    }

    stage('Pre-installation steps') {
        //label nodes
        def openstackNode = runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml get nodes | tail -n +2 | grep -v master | head -1 | awk '{print \$1}'")
        def cephNode = runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml get nodes | tail -n +2 | grep -v master | tail -1 | awk '{print \$1}'")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml label nodes ${openstackNode} openstack-control-plane=enabled")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml label nodes ${openstackNode} openstack-compute-node=enabled")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml label nodes ${openstackNode} openvswitch=enabled")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml label nodes ${cephNode} role=ceph-osd-node")
    }

    stage('Install osh operator') {
        common.retry(2,5){
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml apply -f ${oshRepoDir}/crds/ ")
        }
    }

    stage('Install Ceph') {
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml apply -f https://raw.githubusercontent.com/jumpojoy/os-k8s/master/crds/helmbundle/ceph/rook.yaml")
        //TODO (ogrudev) develop waiter
        sleep(60)
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml apply -f https://raw.githubusercontent.com/jumpojoy/os-k8s/master/crds/ceph/cluster.yaml")
        sleep(60)
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml apply -f https://raw.githubusercontent.com/jumpojoy/os-k8s/master/crds/ceph/storageclass.yaml")
        sleep(60)
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml get secret rook-ceph-admin-keyring -n rook-ceph --export -o yaml | sed -e 's/keyring:/key:/' > ${workspace}/keyring.yml")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml apply -n default -f ${workspace}/keyring.yml")
        def cephPodName = runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml -n rook-ceph get pod -l 'app=rook-ceph-operator' -o jsonpath='{.items[0].metadata.name}'")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml cp rook-ceph/${cephPodName}:/etc/ceph/ceph.conf ${workspace}/ceph.conf && sed -i 's/[a-z]1\\+://g' ${workspace}/ceph.conf")
        sh(script: "sed -i '/^\\[client.admin\\]/d' ${workspace}/ceph.conf", returnStdout: true).trim()
        sh(script: "sed -i '/^keyring =/d' ${workspace}/ceph.conf", returnStdout: true).trim()
        sh(script: "sed -i '/^\$/d' ${workspace}/ceph.conf", returnStdout: true).trim()
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml create configmap rook-ceph-config -n default --from-file=${workspace}/ceph.conf")
    }

    stage('Install Openstack') {
        def k8sDns = runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml get configmap -n kube-system coredns -o jsonpath='{.data.Corefile}' | grep -oh kaas-kubernetes-[[:alnum:]]*")
        sh(script: "sed -i 's/kaas-kubernetes-3af5ae538cf411e9a6c7fa163e5a4837/${k8sDns}/g' ${oshRepoDir}/examples/stein/${OPENSTACK_CONTEXT_NAME}.yaml", returnStdout: true).trim()
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml create namespace openstack")
        runKubectlCmd(workspace, "--kubeconfig=${workspace}/${kubeconfigNewClusterName}.yml apply -f ${oshRepoDir}/examples/stein/${OPENSTACK_CONTEXT_NAME}.yaml")
    }

    stage('Cleanup') {
        deleteDir()
    }
}
