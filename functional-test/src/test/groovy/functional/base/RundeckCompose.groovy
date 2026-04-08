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

    /**
     * Dump recent Rundeck service logs to stderr (e.g. after failed project import). Only works while compose is up.
     */
    void appendRundeckContainerLogsToStdErr(String header, int maxChars = 120_000) {
        try {
            def opt = getContainerByServiceName("rundeck")
            if (!opt.isPresent()) {
                System.err.println("${header}\n(no rundeck service container in compose state)")
                return
            }
            String logs = opt.get().getLogs()
            if (logs == null) {
                System.err.println("${header}\n(logs null)")
                return
            }
            if (logs.length() > maxChars) {
                int start = logs.length() - maxChars
                logs = "(truncated to last ${maxChars} chars of ${logs.length()})\n" + logs.substring(start)
            }
            System.err.println("${header}\n${logs}")
        } catch (Exception e) {
            System.err.println("${header}\n(could not read container logs: ${e.class.name}: ${e.message})")
        }
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
