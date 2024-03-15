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
            println(result)
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
