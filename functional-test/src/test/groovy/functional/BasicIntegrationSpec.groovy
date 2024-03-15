package functional

import functional.util.TestUtil
import okhttp3.RequestBody
import org.rundeck.client.RundeckClient
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import org.rundeck.client.api.model.ExecutionStateResponse
import org.rundeck.client.api.model.JobRun
import org.rundeck.client.api.model.ProjectItem
import org.rundeck.client.util.Client
import org.testcontainers.containers.DockerComposeContainer
import spock.lang.Shared
import spock.lang.Specification
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.spock.Testcontainers

import java.time.Duration

@Testcontainers
class BasicIntegrationSpec extends Specification {

    @Shared
    public static DockerComposeContainer rundeckEnvironment =
            new DockerComposeContainer(new File("src/test/resources/docker/docker-compose.yml"))
                    .withExposedService("rundeck", 4440,
                            Wait.forHttp("/api/41/system/info").forStatusCode(403).withStartupTimeout(Duration.ofMinutes(5))
                    )


    @Shared
    Client<RundeckApi> client

    static String PROJ_NAME = 'ansible-test'

    static class TestLogger implements Client.Logger {
        @Override
        void output(String out) {
            println(out)
        }

        @Override
        void warning(String warn) {
            System.err.println(warn)
        }

        @Override
        void error(String err) {
            System.err.println(err)
        }
    }

    def setup() {
        rundeckEnvironment.start()
        String address = rundeckEnvironment.getServiceHost("rundeck",4440)
        Integer port = 4440
        def rdUrl = "http://${address}:${port}/api/41"
        System.err.println("rdUrl: $rdUrl")
        client = RundeckClient.builder().with {
            baseUrl rdUrl
            passwordAuth('admin', 'admin')
            //tokenAuth 'letmeinplease'// token.token
            logger(new TestLogger())
            build()
        }

        def projList = client.apiCall(api -> api.listProjects())

        if (!projList*.name.contains(PROJ_NAME)) {
            def project = client.apiCall(api -> api.createProject(new ProjectItem(name: PROJ_NAME)))
        }

        //import test project
        File projectFile = TestUtil.createArchiveJarFile(PROJ_NAME, new File("src/test/resources/project-import/ansible-test"))
        RequestBody body = RequestBody.create(projectFile, Client.MEDIA_TYPE_ZIP)
        client.apiCall(api ->
                api.importProjectArchive(PROJ_NAME,  "preserve", true, true, true, true, true, true, true, [:], body)
        )

        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,".*")}

        while(result.get("ssh-node")==null){
            sleep(2000)
            result = client.apiCall {api-> api.listNodes(PROJ_NAME,".*")}
        }
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
