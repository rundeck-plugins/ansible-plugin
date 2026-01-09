package functional

import functional.base.BaseTestConfiguration
import functional.util.TestUtil
import org.rundeck.client.api.model.JobRun
import org.testcontainers.spock.Testcontainers

@Testcontainers
class MultiNodeAuthSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-multi-node-auth'
    static String NODE1_NAME = "ssh-node"
    static String NODE2_NAME = "ssh-node-2"
    static String NODE3_NAME = "ssh-node-3"

    // Test passwords for each node (matching docker-compose.yml)
    static String NODE1_PASSWORD = "testpassword123"
    static String NODE2_PASSWORD = "password2_special!@#"
    static String NODE3_PASSWORD = 'password3"quote\'test'

    def setupSpec() {
        startCompose()
        configureMultiNodeAuthProject()
    }

    def configureMultiNodeAuthProject() {
        // Store passwords for each node in Rundeck key storage
        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
            NODE1_PASSWORD.getBytes(),
            org.rundeck.client.util.Client.MEDIA_TYPE_X_RUNDECK_PASSWORD
        )
        client.apiCall { api ->
            api.createKeyStorage("project/$PROJ_NAME/ssh-node.pass", requestBody)
        }

        requestBody = okhttp3.RequestBody.create(
            NODE2_PASSWORD.getBytes(),
            org.rundeck.client.util.Client.MEDIA_TYPE_X_RUNDECK_PASSWORD
        )
        client.apiCall { api ->
            api.createKeyStorage("project/$PROJ_NAME/ssh-node-2.pass", requestBody)
        }

        requestBody = okhttp3.RequestBody.create(
            NODE3_PASSWORD.getBytes(),
            org.rundeck.client.util.Client.MEDIA_TYPE_X_RUNDECK_PASSWORD
        )
        client.apiCall { api ->
            api.createKeyStorage("project/$PROJ_NAME/ssh-node-3.pass", requestBody)
        }

        // Create project
        def projList = client.apiCall { api -> api.listProjects() }
        if (!projList*.name.contains(PROJ_NAME)) {
            client.apiCall { api ->
                api.createProject(new org.rundeck.client.api.model.ProjectItem(name: PROJ_NAME))
            }
        }

        // Import project configuration
        File projectFile = TestUtil.createArchiveJarFile(
            PROJ_NAME,
            new File("src/test/resources/project-import/$PROJ_NAME")
        )
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
            projectFile,
            org.rundeck.client.util.Client.MEDIA_TYPE_ZIP
        )
        client.apiCall { api ->
            api.importProjectArchive(
                PROJ_NAME,
                "preserve",
                true, true, true, true, true, true, true,
                [:],
                body
            )
        }

        // Wait for nodes to be available
        waitForNodeAvailability(PROJ_NAME, NODE1_NAME)
        waitForNodeAvailability(PROJ_NAME, NODE2_NAME)
        waitForNodeAvailability(PROJ_NAME, NODE3_NAME)
    }

    def "test multi-node authentication with different passwords"() {
        when:
        def jobId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"  // multi-node-ping-test job

        JobRun request = new JobRun()
        request.loglevel = 'INFO'

        def result = client.apiCall { api -> api.runJob(jobId, request) }
        def executionId = result.id

        def executionState = waitForJob(executionId)
        def logs = getLogs(executionId)

        then:
        // Job succeeded - this verifies that multi-node authentication worked
        executionState != null
        executionState.getExecutionState() == "SUCCEEDED"

        // Verify all three nodes were in the execution context
        executionState.targetNodes.contains("ssh-node")
        executionState.targetNodes.contains("ssh-node-2")
        executionState.targetNodes.contains("ssh-node-3")
    }

    def "test ansible playbook with multi-node authentication"() {
        when:
        def jobId = "b2c3d4e5-f6a7-8901-bcde-f12345678901"  // ansible-playbook-multi-node-test job

        JobRun request = new JobRun()
        request.loglevel = 'DEBUG'

        def result = client.apiCall { api -> api.runJob(jobId, request) }
        def executionId = result.id

        def executionState = waitForJob(executionId)
        def logs = getLogs(executionId)

        then:
        // Job succeeded - this verifies the playbook ran successfully with multi-node authentication
        executionState != null
        executionState.getExecutionState() == "SUCCEEDED"

        // Verify all three nodes were targeted by the playbook execution
        executionState.targetNodes.contains("ssh-node")
        executionState.targetNodes.contains("ssh-node-2")
        executionState.targetNodes.contains("ssh-node-3")
    }

    def "test nodes are accessible with different credentials"() {
        when:
        // List all nodes in the project
        def nodes = client.apiCall { api -> api.listNodes(PROJ_NAME, ".*") }

        then:
        // Verify all three nodes are registered
        nodes != null
        nodes.size() >= 3
        nodes.get(NODE1_NAME) != null
        nodes.get(NODE2_NAME) != null
        nodes.get(NODE3_NAME) != null
    }

    def "test passwords with special characters are properly escaped"() {
        when:
        def jobId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

        JobRun request = new JobRun()
        request.loglevel = 'DEBUG'
        request.filter = "name: ssh-node-2 || name: ssh-node-3"  // Only test nodes with special chars in passwords

        def result = client.apiCall { api -> api.runJob(jobId, request) }
        def executionId = result.id

        def executionState = waitForJob(executionId)
        def logs = getLogs(executionId)

        then:
        // Job succeeded even with special characters in passwords - verifies password escaping works
        executionState != null
        executionState.getExecutionState() == "SUCCEEDED"

        // Verify nodes with special character passwords were targeted
        executionState.targetNodes.contains("ssh-node-2")
        executionState.targetNodes.contains("ssh-node-3")

        // No YAML parsing errors - verifies special characters were properly escaped
        !logs.any { it.log.toLowerCase().contains("yaml") && it.log.toLowerCase().contains("error") }
    }
}
