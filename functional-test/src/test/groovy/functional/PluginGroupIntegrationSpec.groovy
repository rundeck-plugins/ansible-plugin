package functional

import functional.base.BaseTestConfiguration
import functional.util.TestUtil
import org.rundeck.client.api.model.JobRun
import org.testcontainers.spock.Testcontainers


@Testcontainers
class PluginGroupIntegrationSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-plugin-group-test'
    static String DEFAULT_NODE_NAME = "ssh-node"

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, DEFAULT_NODE_NAME)
    }

    def "test simple inline playbook"(){
        when:

        def jobId = "fa0e401b-b5a8-436a-b13b-0e8092858021"

        JobRun request = new JobRun()
        request.loglevel = 'DEBUG'

        def result = client.apiCall {api-> api.runJob(jobId, request)}
        def executionId = result.id

        def executionState = waitForJob(executionId)

        def logs = getLogs(executionId)
        Map<String, Integer> ansibleNodeExecutionStatus = TestUtil.getAnsibleNodeResult(logs)

        then:
        executionState!=null
        executionState.getExecutionState()=="SUCCEEDED"
        ansibleNodeExecutionStatus.get("ok")!=0
        ansibleNodeExecutionStatus.get("unreachable")==0
        ansibleNodeExecutionStatus.get("failed")==0
        ansibleNodeExecutionStatus.get("skipped")==0
        ansibleNodeExecutionStatus.get("ignored")==0

        logs.findAll {it.log.contains("plugin group set getAnsibleConfigFilePath: /home/rundeck/ansible")}.size() == 1
        logs.findAll {it.log.contains("ANSIBLE_CONFIG: /home/rundeck/ansible")}.size() == 1

    }

    def "test simple inline playbook with env vars"(){
        when:

        def jobId = "572367d2-e41a-4fdb-b6fc-effa32185b61"

        JobRun request = new JobRun()
        request.loglevel = 'DEBUG'

        def result = client.apiCall {api-> api.runJob(jobId, request)}
        def executionId = result.id

        def executionState = waitForJob(executionId)

        def logs = getLogs(executionId)
        Map<String, Integer> ansibleNodeExecutionStatus = TestUtil.getAnsibleNodeResult(logs)

        then:
        executionState!=null
        executionState.getExecutionState()=="SUCCEEDED"
        ansibleNodeExecutionStatus.get("ok")!=0
        ansibleNodeExecutionStatus.get("unreachable")==0
        ansibleNodeExecutionStatus.get("failed")==0
        ansibleNodeExecutionStatus.get("skipped")==0
        ansibleNodeExecutionStatus.get("ignored")==0
        logs.findAll {it.log.contains("plugin group set getAnsibleConfigFilePath: /home/rundeck/ansible")}.size() == 1
        logs.findAll {it.log.contains("plugin group set getEncryptExtraVars: true")}.size() == 1
        logs.findAll {it.log.contains("ANSIBLE_CONFIG: /home/rundeck/ansible")}.size() == 1
        logs.findAll {it.log.contains("encryptVariable password")}.size() == 1
        logs.findAll {it.log.contains("encryptVariable username")}.size() == 1
        logs.findAll {it.log.contains("\"msg\": \"rundeck\"")}.size() == 1
        logs.findAll {it.log.contains("\"msg\": \"demo\"")}.size() == 1

    }

}
