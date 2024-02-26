pipeline {
    parameters {
        string defaultValue: '/home/jaguar/jenkins/', name: 'my_workspace'
        string defaultValue: 'Product_Test_Docker', name: 'my_node'
        string defaultValue: 'product_testing', name: 'allure_title'
        string defaultValue: '/home/jaguar/jenkins/Corsica_Daily_Build_New', name: 'FA_WORKSPACE'
    }
    agent { 
        node{
            label "${params.my_node}"
            customWorkspace "${params.my_workspace}"
        }
    }
    options {
        timeout(time: 3,unit:'HOURS') 
    }
    stages {
        stage('Pull Test Code') {
            steps {
                script{
                    env.Current_Workspace = "${WORKSPACE}"
                    deleteDir()
                    sh "mkdir -p product_testing"
                    dir('product_testing'){
                        echo "Pulling Code"
                        checkout scmGit(branches: [[name: '*/itest']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/aut/product_test.git']])
                        //checkout scmGit(branches: [[name: '*/itest']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/~wenxuan.qiu/product_test.git']])
                    }
                }
            }
        }
        stage('Run Product Testcase') {
            steps {
                dir('product_testing'){
                    script{
                        catchError {
                           sh """source activate py375;robot --listener allure_robotframework:output/allure --loglevel DEBUG -i SW_SMOKE -i SW_UEFI -e SW_NOT ."""
                        //sh """source activate py375;robot --listener allure_robotframework:output/allure --loglevel DEBUG -i SW_TEST -e SW_NOT ."""
                        }
                    }
                }
            }
            
        }
    }
    post('Results') { // 执行之后的操作
        always{
            script{// 集成allure，目录需要和保存的results保持一致，注意此处目录为job工作目录之后的目录，Jenkins会自动将根目录与path进行拼接
            catchError{
                echo "FA_WORKESPACE=${FA_WORKSPACE},workspace=${WORKSPACE}"
                sh """sshpass -p jaguar scp -r ${WORKSPACE}/product_testing/output/allure root@10.21.187.103:${FA_WORKSPACE}/allure_result/product_testing"""
                allure includeProperties: false, jdk: '', report: 'report/allure-report', results: [[path: "product_testing/output/allure"]]
                step(
                        [
                          $class              : 'RobotPublisher',
                          outputPath          : "product_testing",
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