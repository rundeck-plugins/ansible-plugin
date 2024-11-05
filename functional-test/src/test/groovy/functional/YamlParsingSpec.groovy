package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

@Testcontainers
class YamlParsingSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-yaml-parsing'
    static String NODE_1 = 'server1'
    static String NODE_2 = 'server2'

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, 'server1')
    }

    void "hide warnings"() {
        when:
        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,'.*')}

        then:
        result != null
        result.size() == 3
        result.get(NODE_1) != null
        result.get(NODE_1).getAttributes().get("ansible_host") == "192.168.1.10"
        result.get(NODE_1).getAttributes().get("ansible_user") == "user2"
        result.get(NODE_1).getAttributes().get("http_port") == "8080"
        result.get(NODE_2) != null
        result.get(NODE_2).getAttributes().get("ansible_host") == "192.168.1.20"
        result.get(NODE_2).getAttributes().get("ansible_user") == "user3"
        result.get(NODE_2).getAttributes().get("http_port") == "8080"
    }
}
