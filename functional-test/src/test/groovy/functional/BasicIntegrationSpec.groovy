package functional

import functional.util.TestUtil
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import org.rundeck.client.api.model.ExecutionStateResponse
import org.rundeck.client.api.model.JobRun
import org.rundeck.client.util.Client
import spock.lang.Shared
import spock.lang.Specification
import org.testcontainers.spock.Testcontainers


@Testcontainers
class BasicIntegrationSpec extends Specification {

    @Shared
    public static RundeckCompose rundeckEnvironment = new RundeckCompose(new File("src/test/resources/docker/docker-compose.yml").toURI())

    @Shared
    Client<RundeckApi> client

    static String PROJ_NAME = 'ansible-test'

    def setupSpec() {
        rundeckEnvironment.startCompose()
        client = rundeckEnvironment.configureRundeck(PROJ_NAME)
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
        logs.findAll {it.log.contains("encryptVariable ansible_ssh_password:")}.size() == 1
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

    ExecutionStateResponse waitForJob(String executionId){
        def finalStatus = [
                'aborted',
                'failed',
                'succeeded',
                'timedout',
                'other'
        ]

        while(true) {
            ExecutionStateResponse result=client.apiCall { api-> api.getExecutionState(executionId)}
            if (finalStatus.contains(result?.getExecutionState()?.toLowerCase())) {
                return result
            } else {
                sleep (10000)
            }
        }

    }


    List<ExecLog> getLogs(String executionId){
        def offset = 0
        def maxLines = 1000
        def lastmod = 0
        boolean isCompleted = false

        List<ExecLog> logs = []

        while (!isCompleted){
            ExecOutput result = client.apiCall { api -> api.getOutput(executionId, offset,lastmod, maxLines)}
            isCompleted = result.completed
            offset = result.offset
            lastmod = result.lastModified

            logs.addAll(result.entries)

            if(result.unmodified){
                sleep(5000)
            }else{
                sleep(2000)
            }
        }

        return logs
    }





}
