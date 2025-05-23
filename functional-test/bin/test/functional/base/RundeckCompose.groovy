package functional.base

import org.rundeck.client.RundeckClient
import org.rundeck.client.api.RundeckApi
import org.rundeck.client.util.Client
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

class RundeckCompose extends DockerComposeContainer<RundeckCompose> {

    public static final String RUNDECK_IMAGE = System.getenv("RUNDECK_TEST_IMAGE") ?: System.getProperty("RUNDECK_TEST_IMAGE")


    RundeckCompose(URI composeFilePath) {
        super(new File(composeFilePath))

        withExposedService("rundeck", 4440,
                Wait.forHttp("/api/41/system/info").forStatusCode(403).withStartupTimeout(Duration.ofMinutes(5))
        )
        withEnv("RUNDECK_IMAGE", RUNDECK_IMAGE)
        withEnv("NODE_USER_PASSWORD", BaseTestConfiguration.NODE_USER_PASSWORD)
    }

    Client<RundeckApi> getClient(){
        //configure rundeck api
        String address = getServiceHost("rundeck",4440)
        Integer port = getServicePort("rundeck",4440)
        def rdUrl = "http://${address}:${port}/api/41"
        System.err.println("rdUrl: $rdUrl")
        Client<RundeckApi> client = RundeckClient.builder().with {
            baseUrl rdUrl
            passwordAuth('admin', 'admin')
            logger(new TestLogger())
            timeout(300)
            readTimeout(300)
            connectTimeout(300)
            build()
        }
        return client
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
