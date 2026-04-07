package functional.base

import com.fasterxml.jackson.databind.ObjectMapper
import functional.util.TestUtil
import okhttp3.Request
import okhttp3.RequestBody
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import org.rundeck.client.api.model.ExecutionStateResponse
import org.rundeck.client.api.model.ProjectImportStatus
import org.rundeck.client.api.model.ProjectItem
import org.rundeck.client.util.Client
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class BaseTestConfiguration extends Specification{

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()

    @Shared
    Client<RundeckApi> client

    @Shared
    public static RundeckCompose rundeckEnvironment

    public static final String NODE_USER_PASSWORD = "testpassword123"
    public static final String NODE_KEY_PASSPHRASE = "testpassphrase123"
    public static final String USER_VAULT_PASSWORD = "vault123"
    public static final String ENCRYPTED_INVENTORY_VAULT_PASSWORD = "123456"

    def startCompose() {
        if(rundeckEnvironment==null){
            //generate SSH private key for node authentication
            File keyPath = new File("src/test/resources/docker/keys")
            TestUtil.generatePrivateKey(keyPath.getAbsolutePath(),"id_rsa")
            TestUtil.generatePrivateKey(keyPath.getAbsolutePath(),"id_rsa_passphrase", NODE_KEY_PASSPHRASE)

            rundeckEnvironment = new RundeckCompose(new File("src/test/resources/docker/docker-compose.yml").toURI())
            rundeckEnvironment.start()
        }

        client = rundeckEnvironment.getClient()
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

    def configureRundeck(String projectName, String nodeName){

        //add private key
        // OkHttp 4+: MediaType first (classpath resolves okhttp 4.12 from rd-api-client / retrofit)
        RequestBody requestBody = RequestBody.create(Client.MEDIA_TYPE_OCTET_STREAM, new File("src/test/resources/docker/keys/id_rsa"))
        def keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node.key", requestBody)}

        //add private key with passphrase
        requestBody = RequestBody.create(Client.MEDIA_TYPE_OCTET_STREAM, new File("src/test/resources/docker/keys/id_rsa_passphrase"))
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node-passphrase.key", requestBody)}

        //add passphrase
        requestBody = RequestBody.create(Client.MEDIA_TYPE_X_RUNDECK_PASSWORD, NODE_KEY_PASSPHRASE.getBytes())
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node-passphrase.pass", requestBody)}

        //add node user ssh-password
        requestBody = RequestBody.create(Client.MEDIA_TYPE_X_RUNDECK_PASSWORD, NODE_USER_PASSWORD.getBytes())
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node.pass", requestBody)}

        //user vault password
        requestBody = RequestBody.create(Client.MEDIA_TYPE_X_RUNDECK_PASSWORD, USER_VAULT_PASSWORD.getBytes())
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/vault-user.pass", requestBody)}

        //add encrypted inventory password
        requestBody = RequestBody.create(Client.MEDIA_TYPE_X_RUNDECK_PASSWORD, ENCRYPTED_INVENTORY_VAULT_PASSWORD.getBytes())
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/vault-inventory.password", requestBody)}

        // create project — Grails 7 expects top-level name plus config.project.name (see rundeck OSS BaseContainer.setupProjectArchiveFile)
        def projList = client.apiCall { api -> api.listProjects() }

        if (!projList*.name.contains(projectName)) {
            def item = new ProjectItem()
            item.name = projectName
            item.config = [('project.name'): projectName]
            client.apiCall { api -> api.createProject(item) }
        }

        // import project — rd-api-client: PUT project/{project}/import; jobUuidOption preserve; see RundeckApi.importProjectArchive
        File projectFile = TestUtil.createArchiveJarFile(projectName, new File("src/test/resources/project-import/" + projectName))
        RequestBody body = RequestBody.create(Client.MEDIA_TYPE_ZIP, projectFile)
        ProjectImportStatus importStatus = client.apiCall { api ->
            api.importProjectArchive(projectName, "preserve",
                    true, true, true, true, true, true, true, true,
                    body)
        }
        if (!importStatus.getResultSuccess()) {
            throw new IllegalStateException(
                    "Project import failed for '${projectName}': importStatus=${importStatus.importStatus}, successful=${importStatus.successful}, " +
                            "errors=${importStatus.errors}, executionErrors=${importStatus.executionErrors}, aclErrors=${importStatus.aclErrors}")
        }

        waitForNodeAvailability(projectName, nodeName)

    }

    /**
     * Nudge resource providers (Ansible inventory, etc.) like OSS {@code BaseContainer.waitingResourceEnabled}:
     * PUT project/{project}/config/time then poll until the expected node appears.
     */
    def waitForNodeAvailability(String projectName, String nodeName) {
        touchProjectConfigTime(projectName)
        final long deadlineNanos = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        def result = client.apiCall { api -> api.listNodes(projectName, ".*") }

        while (result.get(nodeName) == null && System.nanoTime() < deadlineNanos) {
            touchProjectConfigTime(projectName)
            sleep(3000)
            result = client.apiCall { api -> api.listNodes(projectName, ".*") }
        }
    }

    private void touchProjectConfigTime(String projectName) {
        String base = client.getApiBaseUrl()
        String url = (base.endsWith('/') ? base : base + '/') + "project/${projectName}/config/time"
        String json = JSON_MAPPER.writeValueAsString([value: String.valueOf(System.currentTimeMillis())])
        RequestBody jsonBody = RequestBody.create(Client.MEDIA_TYPE_JSON, json)
        Request httpReq = new Request.Builder()
                .url(url)
                .put(jsonBody)
                .header('Accept', 'application/json')
                .header('Content-Type', 'application/json')
                .build()
        def call = client.getRetrofit().callFactory().newCall(httpReq)
        def resp = call.execute()
        try {
            if (!resp.successful) {
                String err = resp.body()?.string() ?: ''
                throw new IllegalStateException("PUT project config/time failed: HTTP ${resp.code} ${err}")
            }
        } finally {
            resp.close()
        }
    }

}
