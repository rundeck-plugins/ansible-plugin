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
    static String NODE4_NAME = "ssh-node-4"

    // Test passwords for each node (matching docker-compose.yml)
    static String NODE1_PASSWORD = "testpassword123"
    static String NODE2_PASSWORD = "password2_special!@#"
    static String NODE3_PASSWORD = 'password3"quote\'test' // Expected password value: password3"quote'test

    // Private key for node 4
    static String NODE4_PRIVATE_KEY_PATH = "src/test/resources/docker/keys/id_rsa"

    def setupSpec() {
        startCompose()
        configureMultiNodeAuthProject()
    }

    def configureMultiNodeAuthProject() {
        // Store passwords for each node in Rundeck key storage
        storePasswordInKeyStorage("ssh-node.pass", NODE1_PASSWORD)
        storePasswordInKeyStorage("ssh-node-2.pass", NODE2_PASSWORD)
        storePasswordInKeyStorage("ssh-node-3.pass", NODE3_PASSWORD)

        // Store private key for node 4 in Rundeck key storage
        storePrivateKeyInKeyStorage("ssh-node-4.key", NODE4_PRIVATE_KEY_PATH)

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
        waitForNodeAvailability(PROJ_NAME, NODE4_NAME)
    }

    // Helper method to store password in Rundeck key storage
    private void storePasswordInKeyStorage(String keyPath, String password) {
        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
            password.getBytes(),
            org.rundeck.client.util.Client.MEDIA_TYPE_X_RUNDECK_PASSWORD
        )
        client.apiCall { api ->
            api.createKeyStorage("project/$PROJ_NAME/$keyPath", requestBody)
        }
    }

    // Helper method to store private key in Rundeck key storage
    private void storePrivateKeyInKeyStorage(String keyPath, String privateKeyFilePath) {
        File privateKeyFile = new File(privateKeyFilePath)
        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
            privateKeyFile,
            org.rundeck.client.util.Client.MEDIA_TYPE_OCTET_STREAM
        )
        client.apiCall { api ->
            api.createKeyStorage("project/$PROJ_NAME/$keyPath", requestBody)
        }
    }

    // Helper method to execute job and get execution state
    private Map executeJobAndWait(String jobId, String loglevel = 'INFO', String filter = null) {
        JobRun request = new JobRun()
        request.loglevel = loglevel
        if (filter) {
            request.filter = filter
        }

        def result = client.apiCall { api -> api.runJob(jobId, request) }
        def executionId = result.id
        def executionState = waitForJob(executionId)
        def logs = getLogs(executionId)

        return [executionState: executionState, logs: logs, executionId: executionId]
    }

    // Helper method to verify all four nodes are targeted
    private void verifyAllNodesTargeted(def targetNodes) {
        assert targetNodes.contains("ssh-node")
        assert targetNodes.contains("ssh-node-2")
        assert targetNodes.contains("ssh-node-3")
        assert targetNodes.contains("ssh-node-4")
    }

    def "test multi-node authentication with different passwords"() {
        when:
        def jobId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"  // multi-node-ping-test job
        def result = executeJobAndWait(jobId, 'INFO')

        then:
        // Job succeeded - this verifies that multi-node authentication worked (passwords + private keys)
        result.executionState != null
        result.executionState.getExecutionState() == "SUCCEEDED"

        // Verify all four nodes were in the execution context (3 with passwords, 1 with private key)
        verifyAllNodesTargeted(result.executionState.targetNodes)
    }

    def "test ansible playbook with multi-node authentication"() {
        when:
        def jobId = "b2c3d4e5-f6a7-8901-bcde-f12345678901"  // ansible-playbook-multi-node-test job
        def result = executeJobAndWait(jobId, 'DEBUG')

        then:
        // Job succeeded - this verifies the playbook ran successfully with multi-node authentication (mixed auth types)
        result.executionState != null
        result.executionState.getExecutionState() == "SUCCEEDED"

        // Verify all four nodes were targeted by the playbook execution (3 passwords + 1 private key)
        verifyAllNodesTargeted(result.executionState.targetNodes)
    }

    def "test nodes are accessible with different credentials"() {
        when:
        // List all nodes in the project
        def nodes = client.apiCall { api -> api.listNodes(PROJ_NAME, ".*") }

        then:
        // Verify all four nodes are registered (3 with password auth, 1 with private key auth)
        nodes != null
        nodes.size() >= 4
        nodes.get(NODE1_NAME) != null
        nodes.get(NODE2_NAME) != null
        nodes.get(NODE3_NAME) != null
        nodes.get(NODE4_NAME) != null
    }

    def "test passwords with special characters are properly escaped"() {
        when:
        def jobId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        def result = executeJobAndWait(jobId, 'DEBUG', "name: ssh-node-2 || name: ssh-node-3")

        then:
        // Job succeeded even with special characters in passwords - verifies password escaping works
        result.executionState != null
        result.executionState.getExecutionState() == "SUCCEEDED"

        // Verify nodes with special character passwords were targeted
        result.executionState.targetNodes.contains("ssh-node-2")
        result.executionState.targetNodes.contains("ssh-node-3")

        // No YAML parsing errors - verifies special characters were properly escaped
        !result.logs.any { it.log.toLowerCase().contains("yaml") && it.log.toLowerCase().contains("error") }
    }

    def "test private key authentication works in isolation"() {
        when:
        def jobId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"  // multi-node-ping-test job
        def result = executeJobAndWait(jobId, 'DEBUG', "name: ssh-node-4")

        then:
        // Job succeeded with ONLY private key authentication (no passwords)
        result.executionState != null
        result.executionState.getExecutionState() == "SUCCEEDED"

        // Verify only the private key node was targeted
        result.executionState.targetNodes.contains("ssh-node-4")
        result.executionState.targetNodes.size() == 1

        // Verify no password-related errors in logs
        !result.logs.any { it.log.toLowerCase().contains("password") && it.log.toLowerCase().contains("error") }
    }
}
