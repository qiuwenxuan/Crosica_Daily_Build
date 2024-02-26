pipeline {
    parameters {
        booleanParam(name: 'Need_Deploy', defaultValue: false, description: '是否需要部署')
        string defaultValue: '104网络组测试虚拟机', name: 'my_node'
        string defaultValue: '/home/jenkins/workspace/', name: 'my_workspace'
        string defaultValue: '/home/jaguar/jenkins/Corsica_Daily_Build_New', name: 'FA_WORKSPACE'		
    }
    agent { 
        node{
            label "${params.my_node}"
            customWorkspace "${params.my_workspace}/${env.JOB_NAME}"
        }
    }
	environment{
        my_jobName = "${env.JOB_NAME}"
        workspace = "${env.WORKSPACE}"
        git_deploy_url = "ssh://git@bb.jaguarmicro.com:7999/jsitest/corsicaautodeploy.git"
        git_deploy_branch = "master"  
    }  
    options {
        timeout(time: 2,unit:'HOURS') 
    }
    stages {
        stage('0. clean workerspace & Pull Deploy Code') {
            steps {
                catchError {
                    echo 'clean local workerspace'                
                    echo "workspace=${workspace}"
                    echo "params.Need_Deploy=${params.Need_Deploy}"
                    deleteDir() 
                    checkout scmGit(branches: [[name: "${git_deploy_branch}"]], extensions: [], userRemoteConfigs: [[url: "${git_deploy_url}"]])
                }
            }
        }
        stage('1. Jmnd_Net_DeployAndTest Environment Deploy') {            
			when {
		        expression {
		            return (params.Need_Deploy == true)
		        }
		    }
            steps {
                script{
                    echo '部署业务版本'
                    // 部署应用的步骤				
    				catchError {
    					echo '2.1.1 SCP deploy'
    					//sh """python run.py -s"""
    					sh """python run.py -s -u https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/release/release_v2.6.0_bugfix0129/package/crb/scp_flash_crb_5_n2_2.0g_cmn_1.65g.img"""
    					
    					echo ' 2.1.2 IMU deploy'
    					// sh """python run.py -i """
    					sh """python run.py -i -u https://jfrog1.jaguarmicro.com/artifactory/corsica-sw-generic-local/release/imu-version/ubuntu/corsica_1.0.0.B800/crb_5_imu_flash_202401302200.img"""
    					
    					echo ' 2.1.3 N2 reboot'
    					sh """python run.py --reboot"""
    					
    					echo ' 2.1.4 Cloud deploy'
    					sh """python run.py -c --jmnd-config net"""
    					//sh """python run.py -c --release -u https://jfrog1.jaguarmicro.com:443/artifactory/corsica-sw-generic-local/release/full-version/anolis/corsica_1.0.0.B800/corsica_1.0.0.B800_jmnd_rel.tar.gz"""
                    }
				}
            }
        }
        stage('2. Pull Test Code') {
            steps {
                script{
                    deleteDir()
                    echo """params.my_node=${params.my_node},params.my_workspace=${params.my_workspace}"""
                    sh "mkdir -p jmnd_net_testcase"
                    dir('jmnd_net_testcase'){
                        echo "Pulling Code"
                        //checkout scmGit(branches: [[name: 'master']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/~wenxuan.qiu/jmnd_sw_qa.git']])
                        checkout scmGit(branches: [[name: 'master']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/jac/jmnd_sw_qa.git']])
                    }
                }
            }
        }  
        stage('3 Run Cloud BaseTestcase Testcase') {
            steps {
                dir('jmnd_net_testcase/AutomatedScripts-Net'){
					script{
                        catchError {
                            echo "Run jmnd_net_testcase Testcase"
                            sh """mkdir -p /output/allure"""
                            sh """python -m robot --listener allure_robotframework:output/allure --loglevel DEBUG --include ok -v sleep:3 -v fps:200 -s ovs-inline.inline-offload ovs-inline/"""
                        }
                    }
                }
            }
        }
    }
    post('Results') { // 执行之后的操作
        always{
            script{
                catchError {// 集成allure，目录需要和保存的results保持一致，注意此处目录为job工作目录之后的目录，Jenkins会自动将根目录与path进行拼接
                    echo "FA_WORKESPACE=${FA_WORKSPACE},workspace=${WORKSPACE}"
                    sh """sshpass -p jaguar scp -o StrictHostKeyChecking=no -r ${WORKSPACE}/jmnd_net_testcase/AutomatedScripts-Net/output/allure root@10.21.187.103:${FA_WORKSPACE}/allure_result/jmnd_net_testcase"""
                    allure includeProperties: false, jdk: '', report: 'report/allure-report', results: [[path: "jmnd_net_testcase/AutomatedScripts-Net/output/allure"]]
                    step(
                            [
                              $class              : 'RobotPublisher',
                              outputPath          : "jmnd_net_testcase/AutomatedScripts-Net",
                              outputFileName      : 'output.xml',
                              reportFileName      : 'report.html',
                              logFileName         : 'log.html',
                              disableArchiveOutput: false,
                              passThreshold       : 100,
                              unstableThreshold   : 80,
                            ]
                        )
                    echo 'Finished'
                }
            }
        }
    }
}