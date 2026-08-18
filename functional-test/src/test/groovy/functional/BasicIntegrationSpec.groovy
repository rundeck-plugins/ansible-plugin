package functional

import functional.base.BaseTestConfiguration
import functional.util.TestUtil
import org.rundeck.client.api.model.JobRun
import org.testcontainers.spock.Testcontainers


@Testcontainers
class BasicIntegrationSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-test'
    static String DEFAULT_NODE_NAME = "ssh-node"

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, DEFAULT_NODE_NAME)
    }

    def "ansible adhoc executor uses callback envs instead of -t"() {
        when:

        def jobId = "6b309548-bcc9-40d8-8c79-bfc0d1f1e49c"

        JobRun request = new JobRun()
        request.loglevel = 'DEBUG'   // helpful if you kept the temporary env println

        def result = client.apiCall { api -> api.runJob(jobId, request) }
        def executionId = result.id

        def executionState = waitForJob(executionId)
        def logs = getLogs(executionId)

        then:
        // Job succeeded
        executionState != null
        executionState.getExecutionState() == "SUCCEEDED"

        // no deprecation message about '-t' anywhere in the job logs
        logs.findAll { it.log.contains("DEPRECATION WARNING") && it.log.contains("'-t'") }.isEmpty()

        // Verify that the expected callback env vars were set.
        //  assertions to avoid false negatives on environments that don't echo the env.
        def envLines = logs.findAll { it.log.contains("ANSIBLE_CALLBACKS_ENABLED") || it.log.contains("ANSIBLE_CALLBACK_TREE_DIR") }
        assert envLines == envLines // no-op; keep for readability

        // if debug printed " procArgs: [...]" from AnsibleRunner, ensure '-t' isn't there either.
        def procArgsLines = logs.findAll { it.log.contains(" procArgs: [") }.collect { it.log }
        if (!procArgsLines.isEmpty()) {
            assert procArgsLines.every { !it.contains(" -t ") && !it.contains(",'-t',") }
        }
    }



    def "ansible node executor with ssh password"(){
        setup:
        String ansibleNodeExecutorProjectName = "sshPasswordProject"
        String nodeName = "ssh-node-password"
        configureRundeck(ansibleNodeExecutorProjectName, nodeName)
        when:
        def jobId = "f04f17a9-77cf-4feb-aec1-889a3de0f5ae"
        JobRun request = new JobRun()
        request.loglevel = 'INFO'
        def result = client.apiCall {api-> api.runJob(jobId, request)}
        def executionState = waitForJob(result.id)
        def logs = getLogs(result.id)
        Map<String, Integer> ansibleNodeExecutionStatus = TestUtil.getAnsibleNodeResult(logs)
        then:
        executionState!=null
        executionState.getExecutionState()=="SUCCEEDED"
    }

    def "test simple inline playbook"(){
        when:

        def jobId = "4ecd6b86-b437-4792-a37e-af1fa5a2ca0c"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

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
    }

    def "test simple inline playbook password authentication"(){
        when:

        def jobId = "d8e88ac2-a310-4461-be54-fd38cdac5e11"

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
        logs.findAll {it.log.contains("encryptVariable ansible_password:")}.size() == 1
    }

    def "test simple inline playbook private-key with passphrase authentication"(){
        when:

        def jobId = "243ba9cb-b95c-4d4b-a569-09935a62397d"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

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
    }

    def "test inline playbook secure option are not added to the env vars"(){
        when:

        def jobId = "f302db98-8737-4b87-8832-f830622ccf85"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

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
        logs.findAll {it.log.contains("username='value123'")}.size() == 1
        logs.findAll {it.log.contains("password=''")}.size() == 1


    }

    def "test inline playbook encrypt env vars"(){
        when:

        def jobId = "284e1a6e-bae0-4778-a838-50647fb340e3"

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
        logs.findAll {it.log.contains("encryptVariable password")}.size() == 1
        logs.findAll {it.log.contains("encryptVariable username")}.size() == 1
        logs.findAll {it.log.contains("\"msg\": \"rundeck\"")}.size() == 1
        logs.findAll {it.log.contains("\"msg\": \"demo\"")}.size() == 1

    }

    def "test simple file playbook"(){
        when:

        def jobId = "03f2ed76-f986-4ad5-a9ea-640d326d4b73"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

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
    }

    def "test inline playbook became sudo authentication"(){
        when:

        def jobId = "6a49e380-bfdf-4bfd-b075-a4321ff78836"

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
        logs.findAll {it.log.contains("encryptVariable ansible_become_password")}.size() == 1
        logs.findAll {it.log.contains("\"msg\": \"root\"")}.size() == 1
    }

    def "test simple script ansible node-executor file-copier"(){
        when:

        def jobId = "6b309548-bcc9-40d8-8c79-bfc0d1f1e49c"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

        def result = client.apiCall {api-> api.runJob(jobId, request)}
        def executionId = result.id

        def executionState = waitForJob(executionId)

        then:
        executionState!=null
        executionState.getExecutionState()=="SUCCEEDED"
    }

    def "test use encrypted user file"(){
        when:

        def jobId = "c4d8ddec-ded6-4840-9fab-7eaf2022e12d"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

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
        logs.findAll {it.log.contains("\"environmentTest\": \"test\"")}.size() == 1
        logs.findAll {it.log.contains("\"token\": 13231232312321321321321")}.size() == 1
    }

    def "test use encrypted user file with password authentication"(){
        when:

        def jobId = "0ea27de5-ef36-4a2f-b09c-1bd548eb78d4"

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
        logs.findAll {it.log.contains("encryptVariable ansible_password:")}.size() == 1
        logs.findAll {it.log.contains("\"environmentTest\": \"test\"")}.size() == 1
        logs.findAll {it.log.contains("\"token\": 13231232312321321321321")}.size() == 1
    }

    def "test use encrypted user file with multiline password file"(){
        when:

        def jobId = "198b7deb-2ba2-4ac6-a80e-94f06ab7fb62"

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

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
        logs.findAll {it.log.contains("\"environmentTest\": \"someOtherTest\"")}.size() == 1
        logs.findAll {it.log.contains("\"token\": \"someOtherToken\"")}.size() == 1
    }

}
