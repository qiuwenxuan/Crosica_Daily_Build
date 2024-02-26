pipeline {
    parameters {
        string defaultValue: '/home/jenkins/workspace', name: 'my_workspace'
        string defaultValue: '基础支撑用例调试机107', name: 'my_node'
        string defaultValue: 'base_support_testcase', name: 'allure_title'
    }
    
    agent { 
        node{
            label "${params.my_node}"
            customWorkspace "${params.my_workspace}"
        }
    }
    options {
        timeout(time: 1,unit:'HOURS') 
    }
    stages {
        stage('Pull Test Code') {
            steps {
                sh "mkdir -p base_support_testcase"
                dir('base_support_testcase'){
                    deleteDir()
                    echo "Pulling Code"
                    checkout scmGit(branches: [[name: '*/sw_itest']], extensions: [], userRemoteConfigs: [[url: 'ssh://git@bb.jaguarmicro.com:7999/~wenxuan.qiu/autotest.git']])
                }
            }
        }    
        stage('Run BaseSupport Testcase') {
            steps {
                dir("base_support_testcase"){
                    script{
                         catchError {
                            sh """robot --listener allure_robotframework --loglevel DEBUG --test IMU环境拉起 ."""
                         }
                    }
                }
            }
        }
    }
    post('Results') { // 执行之后的操作
        always{
            script{// 集成allure，目录需要和保存的results保持一致，注意此处目录为job工作目录之后的目录，Jenkins会自动将根目录与path进行拼接
                allure includeProperties: false, jdk: '', report: 'report/allure-report', results: [[path: "base_support_testcase/output/allure"]]
                step(
                            [
                              $class              : 'RobotPublisher',
                              outputPath          : "base_support_testcase",
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