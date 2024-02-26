pipeline {
    parameters {
        string defaultValue: '云支撑用例调试机103', name: 'my_node'
        string defaultValue: '/home/jenkins/workspace', name: 'my_workspace'
    }
    agent { 
        node{
            //label 'linux调试机103'
            //customWorkspace "workspace/${params.my_workspace}"
            //label '基础支撑用例调试机107'
            label "${params.my_node}"
            customWorkspace "${params.my_workspace}"
        }
    }
    options {
        timeout(time: 2,unit:'HOURS') 
    }
    stages {
        stage('Pull Test Code') {
            steps {
                deleteDir()
                echo "env.WORKSPACE=${env.WORKSPACE}"
                echo """params.my_node=${params.my_node},params.my_workspace=${params.my_workspace}"""
                //checkout scmGit(branches: [[name: '*/CRB']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/product/sw_itest.git']])
                checkout scmGit(branches: [[name: '*/CRB']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/~wenxuan.qiu/sw_itest_new.git']])
            }
        }  
        stage('Run BaseTestcase Testcase') {
            steps {
                dir('tests/pytestCase'){
					script{
                        catchError {
                            sh """pytest test_blkdev.py::TestLegacyBlkDev::test_blkpf_legacy_driver_ok test_netdev.py::TestLegacyNetDev::test_netpf_legacy_driver_ok --env-type=CRB --env-sub-type=all -vs --alluredir=./report/result --clean-alluredir --junitxml=./report/result.xml"""
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
                    allure includeProperties: false, jdk: '', report: 'report/allure-report', results: [[path: 'tests/pytestCase/report/result']]
                    junit testResults: "tests/pytestCase/report/*.xml", skipPublishingChecks: true
                }
            }
        }
    }
}