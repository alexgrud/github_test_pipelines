// Expected parameters:
//   SALT_MASTER_HOST
common = new com.mirantis.mk.Common()
python = new com.mirantis.mk.Python()
salt = new com.mirantis.mk.Salt()
git = new com.mirantis.mk.Git()
openstack = new com.mirantis.mk.Openstack_test()


node{
    def pipelineRepoUrl = 'ssh://mcp-ci-gerrit@gerrit.mcp.mirantis.com:29418/mcp/mcp-pipelines'
    def pipelineRepoBranch = 'master'
    def workspace = common.getWorkspace()
    def pipelineRepoDir = "${workspace}/pipeline_repo"
    def openstackEnv = "${workspace}/venv"
    def failedBuildResult = 'FAILURE'

    openstackEnvironment = "imc-us"
    openstackProjectName = "oscore-team"
    //UNCOMMENT!
    //stackName = env.CLUSTER_NAME

    //REMOVE!
    stackName = "sgarbuz-deploy-heat-virtual-mcp11-aio-27547"
    GERRIT_CREDENTIALS_ID = "mcp-ci-gerrit"

    def os_openrc = ["OS_PROJECT_NAME": openstackProjectName]

    switch (openstackEnvironment) {
        case 'imc-eu':
            os_openrc['OPENSTACK_ENVIRONMENT'] = 'internal_cloud_v2_eu'
            region = 'eu'
            break
        case 'imc-us':
            os_openrc['OPENSTACK_ENVIRONMENT'] = 'internal_cloud_v2_us'
            region = 'us'
            break
    }

    stage('Cleanup') {
            deleteDir()
        }

        stage('Cloning pipelines repository') {
            git.checkoutGitRepository(pipelineRepoDir, pipelineRepoUrl, pipelineRepoBranch, GERRIT_CREDENTIALS_ID, true, 10, 0)
        }

        stage('Create Openstack venv') {
            def mirantisClouds = readYaml file: "${pipelineRepoDir}/clouds.yaml"
            def openstack_credentials_id, auth_url
            if (mirantisClouds.clouds.containsKey(os_openrc['OPENSTACK_ENVIRONMENT'])) {
                openstack_credentials_id = mirantisClouds.clouds."${os_openrc['OPENSTACK_ENVIRONMENT']}".jenkins_credentials_with_user_pass
                auth_url = mirantisClouds.clouds."${os_openrc['OPENSTACK_ENVIRONMENT']}".auth.auth_url
            } else {
                error("There is no configuration for ${os_openrc['OPENSTACK_ENVIRONMENT']} underlay OpenStack in clouds.yaml")
            }
            rcFile = openstack.createOpenstackEnv(workspace, auth_url, openstack_credentials_id, os_openrc['OS_PROJECT_NAME'], 'default', '', 'default', '3', '')
            openstack.setupOpenstackVirtualenv(openstackEnv)
        }

        //stage('Create keypair') {
        //    openstack.runOpenstackCommand("openstack keypair create ${stackName}", rcFile, openstackEnv)
        //}

        //stage('Delete keypair if exist') {
        //    openstack.ensureKeyPairRemoved("${stackName}", rcFile, openstackEnv)
        //}

        stage('Delete old keypairs') {
            def keypairs = openstack.runOpenstackCommand("openstack keypair list -f value -c Name", rcFile, openstackEnv).tokenize('\n')
            println(keypairs)
            for (keypair in keypairs) {
                if (keypair.startsWith("sgarbuz")) {
                    openstack.deleteKeyPair(rcFile, keypair, openstackEnv)
                }
            }
        }

}

