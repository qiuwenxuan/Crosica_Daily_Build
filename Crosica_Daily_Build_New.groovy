class Count{
    def totalcount = 0
    def passcount = 0
    def failcount = 0
    def passrate = 0
    def Count(){
        println "This is a count class for Corsica_Daily_Build!"
    }
    def Update(int total, int pass, int fail){
        this.totalcount += total
        this.passcount += pass
        this.failcount += fail
    }
    def Getrate(){
        // if语句防止Deploy失败后，totalcount=0而导致除0报错
        if(this.totalcount){  //有统计时
            this.passrate = this.passcount / this.totalcount * 100
            this.passrate = String.format("%.0f", this.passrate) //%.2f
        }
        else{     //无统计时
            this.passrate = 0
        } 
    }
}
def base_support_testing_count = new Count()    //统计基础支撑测试用例
def jmnd_net_tesing_count = new Count()         //统计网络组用例
def cloud_base_testing_count = new Count()      //统计CRB_Smoking用例
def product_testing_count = new Count()         //统计Product_Testing用例
def whole_count = new Count()            //统计构建中的全部用例

pipeline {
    parameters {
        string defaultValue: "/home/jaguar/jenkins/", name: 'my_workspace'
        string defaultValue: 'master', name: 'branch'
        string defaultValue: 'ssh://git@bb.jaguarmicro.com:7999/jsitest/corsicaautodeploy.git', name: 'git_deploy_url'
    }
    agent {
        node {
            //label 'master'
            label '云支撑用例调试机103'
            customWorkspace "${params.my_workspace}/${env.JOB_NAME}"
        }
    }
    environment{
        my_jobName = "${env.JOB_NAME}"
        workspace = "${env.WORKSPACE}"
        Job_Base_Support_Testing = "Base_Support_Testing"
        Job_Base_Support_Workspace = "${workspace}/${Job_Base_Support_Testing}"
        Node_Base_Support_Testing = "基础支撑用例调试机107"
        
        Job_Jmnd_Net_Testing = "Jmnd_Net_DeployAndTest"
        Job_Jmnd_Net_Testing_Workspace = "${workspace}/${Job_Jmnd_Net_Testing}"
        Node_Jmnd_Net_Testing = "104网络组测试虚拟机"
        Job_Jmnd_Net_Testing_Need_Deploy = "true"
        
        Job_Cloud_BaseTestcase_Tesing = "Cloud_BaseTestcase_Tesing"
        Job_Cloud_BaseTestcase_Workspace = "${workspace}/${Job_Cloud_BaseTestcase_Tesing}"
        Node_Cloud_BaseTestcase_Tesing = "云支撑用例调试机103"
        
        Job_Product_Testcase_Testing = "Product_Testcase_Testing"
        Job_Product_Testcase_Workspace = "${workspace}/${Job_Product_Testcase_Testing}"
        Node_Product_Testcase_Testing = "Product_Test_Docker"
        
        
    }  
    options {
        timeout(time: 6,unit:'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('0. clean workerspace & Pull Deploy Code') {
            steps {
                catchError {
                    echo 'clean local workerspace'                
                    echo "workspace=${workspace}"
                    echo "my_jobName=${my_jobName}"
                    deleteDir() 
                    checkout scmGit(branches: [[name: "${branch}"]], extensions: [], userRemoteConfigs: [[url: "${git_deploy_url}"]])
                    sh """mkdir -p ${workspace}/allure_result;rm -rf allure_result/*""" //一定要提前创建号allure_result再构建子job
                }
            }
        }
        stage('1.1 Basic Support Testing Deploy') {
            steps {
                echo "1.1 Basic Support Testing Deploy"
                script{
                    catchError {
                        echo ' 1.1 SCP deploy'
                        sh """python run.py -s"""
                        //sh """python run.py -s -u https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/release/release_v2.6.0_bugfix0129/package/crb/scp_flash_crb_5_n2_2.0g_cmn_1.65g.img"""
						
						echo ' 1.1 IMU deploy'
						sh """python run.py -i"""
                        //sh """python run.py -i --search-url https://jfrog1.jaguarmicro.com:443/artifactory/corsica-soc-generic-local/snapshot/daily-build/ --url-pattern https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/snapshot/daily-build/@{download_date}/package/crb/imu_flash_crb_5.img"""
                        //sh """python run.py -i -u https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/release/release_v2.6.0_bugfix0129/package/crb/imu_flash_crb_5.img"""
                        
                        echo ' 1.3 reboot N2'
                        sh """python run.py --reboot"""
                    }
                }
            }
        }
        stage('1.2 Basic Support Testing') {
            steps {
                echo "1.2 Basic Support Testing"
                script{ 
                    echo """my_workspace=${Job_Base_Support_Workspace},
                            my_node=${Node_Base_Support_Testing}"""
                    catchError {
                            build(job: Job_Base_Support_Testing, parameters: [
                            string(name: 'my_workspace', value: Job_Base_Support_Workspace),
                            string(name: 'my_node', value: Node_Base_Support_Testing),
                            string(name: 'FA_WORKSPACE', value: "${WORKSPACE}")
                        ]) 
                    }
                }
            }
        }
        stage('2.1 Jmnd Net Testcase Deploy And Test'){
            steps{
                echo """2.1 Jmnd Net Testcase Deploy And Test"""
                script{
                    echo """my_workspace=${Job_Jmnd_Net_Testing_Workspace},
                             my_node=${Node_Jmnd_Net_Testing}"""
                    catchError {
                            build(job: Job_Jmnd_Net_Testing, parameters: [
                            string(name: 'Need_Deploy', value: Job_Jmnd_Net_Testing_Need_Deploy),
                            string(name: 'my_workspace', value: Job_Jmnd_Net_Testing_Workspace),
                            string(name: 'my_node', value: Node_Jmnd_Net_Testing),
                            string(name: 'FA_WORKSPACE', value: "${WORKSPACE}")
                        ]) 
                    }
                }
            }
        }
        stage('2.1 Cloud BaseTestcase Tesing Deploy') {
            steps {
                script{
                    echo "2.1 Cloud BaseTestcase Tesing Deploy"
                    catchError {
                        echo ' 2.1.1 SCP deploy'
						sh """python run.py -s"""
                        //sh """python run.py -s -u https://jfrog1.jaguarmicro.com/artifactory/corsica-soc-generic-local/release/release_v2.6.0_bugfix0129/package/crb/scp_flash_crb_5_n2_2.0g_cmn_1.65g.img"""
						
                        echo ' 2.1.2 IMU deploy'
                        sh """python run.py -i """
                        //sh """python run.py -i -u https://jfrog1.jaguarmicro.com/artifactory/corsica-sw-generic-local/release/imu-version/ubuntu/corsica_1.0.0.B800/crb_5_imu_flash_202401291717.img"""
						
                        echo ' 2.1.3 N2 reboot'
                        sh """python run.py --reboot"""
						
                        echo ' 2.1.4 Cloud deploy'
                        sh """python run.py -c"""
                        //sh """python run.py -c --release -u https://jfrog1.jaguarmicro.com:443/artifactory/corsica-sw-generic-local/release/full-version/anolis/corsica_1.0.0.B800/corsica_1.0.0.B800_jmnd_rel.tar.gz"""
                    }
                }
            }
        }
        stage('2.2 Cloud BaseTestcase Tesing') { 
            steps {
                script{ 
                    echo """my_workspace=${Job_Cloud_BaseTestcase_Workspace},
                            my_node=${Node_Cloud_BaseTestcase_Tesing}"""
                    catchError {
                            build(job: Job_Cloud_BaseTestcase_Tesing, parameters: [
                            string(name: 'my_workspace', value: Job_Cloud_BaseTestcase_Workspace),
                            string(name: 'my_node', value: Node_Cloud_BaseTestcase_Tesing),
                            string(name: 'FA_WORKSPACE', value: "${WORKSPACE}")
                        ]) 
                    }
                }
            }
        }
        stage('3.1 Product_Testcase_Testing Deploy') {
            steps {
                script{
                    catchError {
                        echo ' 3.1 Product_Testing Deploy(bm_config.json recovering)'
                        sh """python run.py -r"""
                    }
                }
            }
        }
        stage('3.2 Product_Testcase_Testing') {
            steps {
                script{ 
                    echo """my_workspace=${Job_Product_Testcase_Workspace},
                            my_node=${Node_Product_Testcase_Testing}"""
                    catchError {
                            build(job: Job_Product_Testcase_Testing, parameters: [
                            string(name: 'my_workspace', value: Job_Product_Testcase_Workspace),
                            string(name: 'my_node', value: Node_Product_Testcase_Testing),
                            string(name: 'FA_WORKSPACE', value: "${WORKSPACE}")
                        ]) 
                    }
                }
            }
        }
        stage('10.Statistics') {
            steps {
                script{ 
                    catchError {
                    
                        // sh """sshpass -p jaguar scp -r root@10.21.187.107:${env.Base_Support_Workspace}/base_support_testcase/output/allure ${workspace}/allure_result/base_support_testing"""
                        // sh """sshpass -p jaguar scp -r jaguar@10.21.187.103:${env.Cloud_BaseTestcase_Workspace}/tests/pytestCase/report/result ${workspace}/allure_result/Cloud_BaseTestcase_Tesing"""
                        // sh """sshpass -p jaguar scp -r root@10.21.187.93:${env.Product_Testcase_Workspace}/product_testing/output/allure ${workspace}/allure_result/product_testing"""
                        
                        allure includeProperties: false, jdk: '', report: 'report/allure-report', results: [[path: "allure_result/product_testing"], [path: "allure_result/base_support_testing"], [path: "allure_result/Cloud_BaseTestcase_Tesing"], [path: "allure_result/jmnd_net_testcase"]]
            
                        // 基础支撑数据获取和统计
                        def job_base_support = Jenkins.instance.getItemByFullName(Job_Base_Support_Testing)  
                        def build_base_support = job_base_support.getLastBuild()
                        println("Hello world!     Job_Base_Support_Testing:" + Job_Base_Support_Testing )
                        def testResult = build_base_support.getAction(hudson.plugins.robot.RobotBuildAction)
                        if (testResult) {
                            base_support_testing_count.Update(testResult.totalCount, testResult.totalCount - testResult.failCount - testResult.skipCount, testResult.failCount)
                            base_support_testing_count.Getrate()
                            echo "--------------base_support_testing_count.totalcount：${base_support_testing_count.totalcount}"
                            echo "--------------base_support_testing_count.passrate：${base_support_testing_count.passrate}"
                            whole_count.Update(testResult.totalCount, testResult.totalCount - testResult.failCount - testResult.skipCount, testResult.failCount)
                            echo "--------------whole_count.totalcount：${whole_count.totalcount}"
                        }
                        // 网络组数据获取和统计
                        def job_jmnd_net_test = Jenkins.instance.getItemByFullName(Job_Jmnd_Net_Testing)  
                        def build_jmnd_net_test = job_jmnd_net_test.getLastBuild()
                        println("Hello world!     Job_Jmnd_Net_Testing:" + Job_Jmnd_Net_Testing)
                        def testResult_net = build_jmnd_net_test.getAction(hudson.plugins.robot.RobotBuildAction)
                        if (testResult_net) {
                            jmnd_net_tesing_count.Update(testResult_net.totalCount, testResult_net.totalCount - testResult_net.failCount - testResult_net.skipCount, testResult_net.failCount)
                            jmnd_net_tesing_count.Getrate()
                            echo "--------------jmnd_net_tesing_count.totalcount：${jmnd_net_tesing_count.totalcount}"
                            echo "--------------jmnd_net_tesing_count.passrate：${jmnd_net_tesing_count.passrate}"
                            whole_count.Update(testResult_net.totalCount, testResult_net.totalCount - testResult_net.failCount - testResult_net.skipCount, testResult_net.failCount)
                            echo "--------------whole_count.totalcount：${whole_count.totalcount}"
                        }
                        // 云支撑数据获取和统计
                        def job_cloud_basetest = Jenkins.instance.getItemByFullName(Job_Cloud_BaseTestcase_Tesing) 
                        def build_cloud_basetest = job_cloud_basetest.getLastBuild()
                        def testResult_cloud = build_cloud_basetest.getAction(hudson.tasks.junit.TestResultAction)
                        if (testResult_cloud) {
                            cloud_base_testing_count.Update(testResult_cloud.totalCount, testResult_cloud.totalCount - testResult_cloud.failCount - testResult_cloud.skipCount, testResult_cloud.failCount)
                            cloud_base_testing_count.Getrate()
                            echo "--------------cloud_base_testing_count.totalcount：${cloud_base_testing_count.totalcount}"
                            echo "--------------cloud_base_testing_count.passrate：${cloud_base_testing_count.passrate}"
                            whole_count.Update(testResult_cloud.totalCount, testResult_cloud.totalCount - testResult_cloud.failCount - testResult_cloud.skipCount, testResult_cloud.failCount)
                            echo "--------------whole_count.totalcount：${whole_count.totalcount}"
                        }
                        // 产品测试数据获取和统计
                        def job_product_test = Jenkins.instance.getItemByFullName(Job_Product_Testcase_Testing)  
                        def build_product_test = job_product_test.getLastBuild()
                        def testResult_product_test = build_product_test.getAction(hudson.plugins.robot.RobotBuildAction)
                        if (testResult_product_test) {
                            product_testing_count.Update(testResult_product_test.totalCount, testResult_product_test.totalCount - testResult_product_test.failCount - testResult_product_test.skipCount, testResult_product_test.failCount)
                            product_testing_count.Getrate()
                            echo "--------------product_testing_count.totalcount：${product_testing_count.totalcount}"
                            echo "--------------product_testing_count.passrate：${product_testing_count.passrate}"
                            whole_count.Update(testResult_product_test.totalCount, testResult_product_test.totalCount - testResult_product_test.failCount - testResult_product_test.skipCount, testResult_product_test.failCount)
                            whole_count.Getrate()
                            echo "--------------whole_count.totalcount：${whole_count.totalcount}"
                        
                        }     
                    } 
                }
            }
        
        }
    }
    post{
        always{
            script{
                echo 'post success'
            }
            emailext body: """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>${env.JOB_NAME}--第${env.BUILD_NUMBER}次构建日志</title>
</head>
<body leftmargin="8" marginwidth="0" topmargin="8" marginheight="4" offset="0">

<p>（本邮件由系统自动发出，无需回复！）</p>
<p>以下为${env.JOB_NAME}项目构建信息 &mdash;&mdash; <span style="color: #e03e2d;">构建结果： \${BUILD_STATUS}</span></p>

<table width="95%" cellpadding="0" cellspacing="0" style="font-size: 11pt; font-family: Tahoma, Arial, Helvetica, sans-serif">
	<tr>  
        <td>
            <br/>  
            <b style="color: #000000; font-size: 120%;">构建信息</b>  
            <hr size="2" width="100%" align="center" />
        </td>
    </tr>  
    <tr><td><ul>
    	<li>[项目名称]：${env.JOB_NAME}</li>
		<li>[项目名称]：第${env.BUILD_NUMBER}次构建</li>
		<li>[触发原因]：\${CAUSE}</li>
		<li>[构建状态]：\${BUILD_STATUS}</li>
		<li>[构建日志]：<a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a></li>
		<li>[构建URL]：<a href="${env.BUILD_URL}">${env.BUILD_URL}</a></li>
		<li>[工作目录]：<a href="${env.BUILD_URL}ws">${env.BUILD_URL}ws</a></li>
		<li>[项目URL]：<a href="${env.JOB_URL}">${env.JOB_URL}</a></li>
		<li>[测试报告URL]：<a href="${env.BUILD_URL}/allure/">${env.BUILD_URL}allure/</a></li>
    </ul></td></tr>
    <tr><td>【注】：如需查看更多细节，请将URL链接复制到VDI中的浏览器中打开。</tr></td>

    <tr>  
        <td>
            <br/>  
            <b style="color: #000000; font-size: 120%;">测试用例分析</b>  
            <hr size="2" width="100%" align="center" />
        </td>
    </tr> 
</table>
<table style="border-collapse: collapse; width: 60.4011%; height: 58.8px;" border="2">
<tbody>
<tr style="height: 20px;">
<td style="width: 28.8585%; height: 20px; text-align: center;background-color: #ced3d9;">项目名称</td>
<td style="width: 14.6869%; height: 20px; text-align: center;background-color: #ced3d9;">总用例数</td>
<td style="width: 16.7483%; height: 20px; text-align: center;background-color: #ced3d9;">成功用例数</td>
<td style="width: 14.9446%; height: 20px; text-align: center;background-color: #ced3d9;"><span style="color: #000000;">失败用例数</span></td>
<td style="width: 24.7359%; height: 20px; text-align: center;background-color: #ced3d9;">测试通过率</td>
</tr>

<tr style="height: 20px;">
<td style="width: 28.8585%; height: 20px; text-align: center;">基础支撑用例</td>
<td style="width: 14.6869%; height: 20px; text-align: center;">${base_support_testing_count.totalcount}</td>
<td style="width: 16.7483%; height: 20px; text-align: center;"><span style="color: #2dc26b;">${base_support_testing_count.passcount}</span></td>
<td style="width: 14.9446%; height: 20px; text-align: center;-"><span style="color: #e03e2d;">${base_support_testing_count.failcount}</span></td>
<td style="width: 24.7359%; height: 20px; text-align: center;-"><strong>${base_support_testing_count.passrate}%</strong></td>
</tr>
<tr style="height: 20px;">
<td style="width: 28.8585%; height: 20px; text-align: center;">云支撑基础用例统计</td>
<td style="width: 14.6869%; height: 20px; text-align: center;">${cloud_base_testing_count.totalcount}</td>
<td style="width: 16.7483%; height: 20px; text-align: center;"><span style="color: #2dc26b;">${cloud_base_testing_count.passcount}</span></td>
<td style="width: 14.9446%; height: 20px; text-align: center;-"><span style="color: #e03e2d;">${cloud_base_testing_count.failcount}</span></td>
<td style="width: 24.7359%; height: 20px; text-align: center;-"><strong>${cloud_base_testing_count.passrate}%</strong></td>
</tr>

<tr style="height: 20px;">
<td style="width: 28.8585%; height: 20px; text-align: center;">继承产品测试部高优用例统计</td>
<td style="width: 14.6869%; height: 20px; text-align: center;">${product_testing_count.totalcount}</td>
<td style="width: 16.7483%; height: 20px; text-align: center;"><span style="color: #2dc26b;">${product_testing_count.passcount}</span></td>
<td style="width: 14.9446%; height: 20px; text-align: center;"><span style="color: #e03e2d;">${product_testing_count.failcount}</span></td>
<td style="width: 24.7359%; height: 20px; text-align: center;"><strong>${product_testing_count.passrate}%</strong></td>
</tr>

<tr style="height: 20px;">
<td style="width: 28.8585%; height: 20px; text-align: center;">总计用例统计</td>
<td style="width: 14.6869%; height: 20px; text-align: center;">${whole_count.totalcount}</td>
<td style="width: 16.7483%; height: 20px; text-align: center;"><span style="color: #2dc26b;">${whole_count.passcount}</span></td>
<td style="width: 14.9446%; height: 20px; text-align: center;"><span style="color: #e03e2d;">${whole_count.failcount}</span></td>
<td style="width: 24.7359%; height: 20px; text-align: center;"><strong>${whole_count.passrate}%</strong></td>
</tr>
</tbody>
</table>
<p>&nbsp;</p>
<p>&nbsp;</p>
<p>&nbsp;</p>

</body>
</html>""", subject: "Corsica版本每日构建:\${BUILD_STATUS} - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER} -by @\${BUILD_USER}", 
to: 'wenxuan.qiu@jaguarmicro.com'
        }
    success {
                //当此Pipeline成功时打印消息
                echo 'success'
                
        }
    }
}