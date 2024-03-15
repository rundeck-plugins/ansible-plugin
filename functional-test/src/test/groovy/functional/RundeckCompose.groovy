package functional

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import functional.util.TestUtil
import okhttp3.RequestBody
import org.rundeck.client.RundeckClient
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.ProjectItem
import org.rundeck.client.util.Client
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration

class RundeckCompose extends DockerComposeContainer<RundeckCompose> {

    public static final String RUNDECK_IMAGE = System.getenv("RUNDECK_TEST_IMAGE") ?: System.getProperty("RUNDECK_TEST_IMAGE")
    public static final String NODE_USER_PASSWORD = "testpassword123"
    public static final String NODE_KEY_PASSPHRASE = "testpassphrase123"
    public static final String USER_VAULT_PASSWORD = "vault123"

    RundeckCompose(URI composeFilePath) {
        super(new File(composeFilePath))

        withExposedService("rundeck", 4440,
                Wait.forHttp("/api/41/system/info").forStatusCode(403).withStartupTimeout(Duration.ofMinutes(5))
        )
        withEnv("RUNDECK_IMAGE", RUNDECK_IMAGE)
        withEnv("NODE_USER_PASSWORD", NODE_USER_PASSWORD)
    }


    def startCompose() {
        //generate SSH private key for node authentication
        File keyPath = new File("src/test/resources/docker/keys")
        generatePrivateKey(keyPath.getAbsolutePath(),"id_rsa")
        generatePrivateKey(keyPath.getAbsolutePath(),"id_rsa_passphrase", NODE_KEY_PASSPHRASE)

        start()
    }


    Client<RundeckApi> configureRundeck(String projectName){

        //configure rundeck api
        String address = getServiceHost("rundeck",4440)
        Integer port = 4440
        def rdUrl = "http://${address}:${port}/api/41"
        System.err.println("rdUrl: $rdUrl")
        Client<RundeckApi> client = RundeckClient.builder().with {
            baseUrl rdUrl
            passwordAuth('admin', 'admin')
            logger(new TestLogger())
            build()
        }
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

        //create project
        def projList = client.apiCall(api -> api.listProjects())

        if (!projList*.name.contains(projectName)) {
            def project = client.apiCall(api -> api.createProject(new ProjectItem(name: projectName)))
        }

        //import project
        File projectFile = TestUtil.createArchiveJarFile(projectName, new File("src/test/resources/project-import/ansible-test"))
        RequestBody body = RequestBody.create(projectFile, Client.MEDIA_TYPE_ZIP)
        client.apiCall(api ->
                api.importProjectArchive(projectName,  "preserve", true, true, true, true, true, true, true, [:], body)
        )

        //wait for node to be available
        def result = client.apiCall {api-> api.listNodes(projectName,".*")}
        def count =0

        while(result.get("ssh-node")==null && count<5){
            sleep(2000)
            result = client.apiCall {api-> api.listNodes(projectName,".*")}
            count++
        }

        return client
    }

    static def generatePrivateKey(String filePath, String keyName, String passphrase = null){
        JSch jsch=new JSch()
        KeyPair keyPair=KeyPair.genKeyPair(jsch, KeyPair.RSA)
        if(passphrase){
            keyPair.writePrivateKey(filePath + File.separator + keyName, passphrase.getBytes())
        }else{
            keyPair.writePrivateKey(filePath + File.separator + keyName)
        }

        keyPair.writePublicKey(filePath + File.separator + keyName + ".pub", "test private key")

        keyPair.dispose()

        File privateKey = new File(filePath + File.separator + keyName)
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>()
        perms.add(PosixFilePermission.OWNER_READ)
        perms.add(PosixFilePermission.OWNER_WRITE)
        Files.setPosixFilePermissions(privateKey.toPath(), perms)
    }


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

}
