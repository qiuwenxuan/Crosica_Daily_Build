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
        git_deploy_url = "ssh://git@bb.jaguarmicro.com:7999/~wenxuan.qiu/corsicaautodeploy.git"
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
                                        //sh """python run.py -s --config ./config/env68.conf"""
                                        sh """python run.py -s --config ./config/env68.conf -u https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/release/corsica_v2.6.1_1.0.0.B810.bugfix0227/package/crb/scp_flash_crb_5_n2_2.0g_cmn_1.65g.img"""
                                        //sh """python run.py -s --config ./config/env77.conf -u https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/release/corsica_v2.6.1_1.0.0.B810/package/crb/scp_flash_crb_7_n2_2.4g_cmn_1.85g.img"""

                                        echo ' 2.1.2 IMU deploy'
                                        // sh """python run.py -i --config ./config/env77.conf"""
                                        sh """python run.py -i --config ./config/env68.conf -uhttps://jfrog1.jaguarmicro.com/artifactory/corsica-sw-generic-local/release/imu-version/ubuntu/corsica_1.0.0.B810/crb_5_imu_flash_202402281102.img"""
                                        //sh """python run.py -i --config ./config/env77.conf -u https://jfrog1.jaguarmicro.com/artifactory/corsica-sw-generic-local/release/imu-version/ubuntu/corsica_1.0.0.B810.Pre/crb_7_imu_flash_202402211443.img"""

                                        echo ' 2.1.3 N2 reboot '
                                        sh """python run.py --config ./config/env68.conf --reboot"""

                                        echo ' 2.1.4 Cloud deploy'
                                        //sh """python run.py -c --jmnd-config net --config ./config/env77.conf"""
                                        //sh """python run.py -c --jmnd-config net --config ./config/env68.conf -u https://jfrog1.jaguarmicro.com:443/artifactory/corsica-sw-generic-local/release/full-version/anolis/corsica_1.0.0.B810/corsica_1.0.0.B810_jmnd_rel.tar.gz"""
                                        sh """python run.py -c --release --jmnd-config net --config ./config/env68.conf -u https://jfrog1.jaguarmicro.com:443/artifactory/corsica-sw-generic-local/release/full-version/anolis/corsica_1.0.0.B810.Pre/corsica_1.0.0.B810.Pre_jmnd.tar.gz"""
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
                        checkout scmGit(branches: [[name: 'master']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/jac/jmnd_sw_qa.git']])
                    }
                }
            }
        }
        stage('3 Run Jmnd Net Testcase') {
            steps {
                dir('jmnd_net_testcase/AutomatedScripts-Net'){
                                        script{
                        catchError {
                            echo "Run jmnd_net_testcase Testcase"
                            sh """mkdir -p ../output/allure""" // 这一步必须有，创建allure文件夹存放allure测试报告文件
                            sh """cp -rf /home/smoke/68configsystem.robot ./testlib/config/configsystem.robot""" // 这一步必须有，由于网络组用例拉取的代码没有环境配置文件，我把68,77环境的配置文件放在/home/smoke/文件夹下面，需要跑那个环境就用哪个配置文件拷贝到/testlib/config/configsystem.robot
                            //sh """python -m robot --listener allure_robotframework:../output/allure --loglevel DEBUG --include ok -v sleep:3 -v fps:200 -s ovs-inline.inline-offload.ovs-match-act ovs-inline/""" //测试用的命令，只跑少量的ovs-match-act下的6个测试用例
                            sh """python -m robot --listener allure_robotframework:../output/allure --loglevel DEBUG --include ok -v sleep:3 -v fps:200 -s ovs-inline.inline-offload ovs-inline/""" //跑全量的用例
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
                    sh """sshpass -p jaguar scp -o StrictHostKeyChecking=no -r ${WORKSPACE}/jmnd_net_testcase/output/allure root@10.21.187.103:${FA_WORKSPACE}/allure_result/jmnd_net_testcase"""
                    allure includeProperties: false, jdk: '', report: 'report/allure-report', results: [[path: "jmnd_net_testcase/output/allure"]]
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