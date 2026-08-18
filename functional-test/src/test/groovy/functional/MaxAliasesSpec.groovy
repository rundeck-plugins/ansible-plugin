package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

@Testcontainers
class MaxAliasesSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-max-aliases'
    static String NODE_1 = 'proxy-1.example.net'
    static String NODE_2 = 'proxy-2.example.net'
    static String NODE_3 = 'proxy-3.example.net'

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, NODE_1)
    }

    void "max aliases"() {
        when:
        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,'.*')}

        then:
        result != null
        result.size() == 4
        result.get(NODE_1) != null
        result.get(NODE_1).getAttributes().get('nodename') == NODE_1
        result.get(NODE_1).getAttributes().get('hostname') == NODE_1
        result.get(NODE_1).getAttributes().get('tags') == 'fr, fr1'
        result.get(NODE_2) != null
        result.get(NODE_2).getAttributes().get('nodename') == NODE_2
        result.get(NODE_2).getAttributes().get('hostname') == NODE_2
        result.get(NODE_2).getAttributes().get('tags') == 'fr, fr1'
        result.get(NODE_3) != null
        result.get(NODE_3).getAttributes().get('nodename') == NODE_3
        result.get(NODE_3).getAttributes().get('hostname') == NODE_3
        result.get(NODE_3).getAttributes().get('tags') == 'fr2'
    }
}
