package functional.base

import functional.util.TestUtil
import okhttp3.RequestBody
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import org.rundeck.client.api.model.ExecutionStateResponse
import org.rundeck.client.api.model.ProjectItem
import org.rundeck.client.util.Client
import spock.lang.Shared
import spock.lang.Specification

class BaseTestConfiguration extends Specification{

    @Shared
    Client<RundeckApi> client

    @Shared
    public static RundeckCompose rundeckEnvironment

    public static final String NODE_USER_PASSWORD = "testpassword123"
    public static final String NODE_KEY_PASSPHRASE = "testpassphrase123"
    public static final String USER_VAULT_PASSWORD = "vault123"
    public static final String ENCRYPTED_INVENTORY_VAULT_PASSWORD = "123456"
    public static final String USER_VAULT_PASSWORD_FILE_MULTILINE = "multiline\npassword\n"

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
        RequestBody requestBody = RequestBody.create(new File("src/test/resources/docker/keys/id_rsa"), Client.MEDIA_TYPE_OCTET_STREAM)
        def keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node.key", requestBody)}

        //add private key with passphrase
        requestBody = RequestBody.create(new File("src/test/resources/docker/keys/id_rsa_passphrase"), Client.MEDIA_TYPE_OCTET_STREAM)
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node-passphrase.key", requestBody)}

        //add passphrase
        requestBody = RequestBody.create(NODE_KEY_PASSPHRASE.getBytes(), Client.MEDIA_TYPE_X_RUNDECK_PASSWORD)
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node-passphrase.pass", requestBody)}

        //add node user ssh-password
        requestBody = RequestBody.create(NODE_USER_PASSWORD.getBytes(), Client.MEDIA_TYPE_X_RUNDECK_PASSWORD)
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/ssh-node.pass", requestBody)}

        //user vault password
        requestBody = RequestBody.create(USER_VAULT_PASSWORD.getBytes(), Client.MEDIA_TYPE_X_RUNDECK_PASSWORD)
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/vault-user.pass", requestBody)}

        //user vault password
        requestBody = RequestBody.create(USER_VAULT_PASSWORD_FILE_MULTILINE.getBytes(), Client.MEDIA_TYPE_X_RUNDECK_PASSWORD)
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/vault-multiline.pass", requestBody)}

        //add encrypted inventory password
        requestBody = RequestBody.create(ENCRYPTED_INVENTORY_VAULT_PASSWORD.getBytes(), Client.MEDIA_TYPE_X_RUNDECK_PASSWORD)
        keyResult = client.apiCall {api-> api.createKeyStorage("project/$projectName/vault-inventory.password", requestBody)}

        //create project
        def projList = client.apiCall(api -> api.listProjects())

        if (!projList*.name.contains(projectName)) {
            def project = client.apiCall(api -> api.createProject(new ProjectItem(name: projectName)))
        }

        //import project
        File projectFile = TestUtil.createArchiveJarFile(projectName, new File("src/test/resources/project-import/" + projectName))
        RequestBody body = RequestBody.create(projectFile, Client.MEDIA_TYPE_ZIP)
        client.apiCall(api ->
                api.importProjectArchive(projectName,  "preserve", true, true, true, true, true, true, true, [:], body)
        )

        waitForNodeAvailability(projectName, nodeName)

    }

    def waitForNodeAvailability(String projectName, String nodeName){
        def result = client.apiCall {api-> api.listNodes(projectName,".*")}
        def count =0

        while(result.get(nodeName)==null && count<5){
            sleep(2000)
            result = client.apiCall {api-> api.listNodes(projectName,".*")}
            count++
        }
    }

}
