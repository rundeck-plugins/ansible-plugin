package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

import java.util.concurrent.TimeUnit

@Testcontainers
class MaxAliasesSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-max-aliases'
    static String NODE_1 = 'proxy-1.example.net'
    static String NODE_2 = 'proxy-2.example.net'
    static String NODE_3 = 'proxy-3.example.net'
    static String RESOLVE_NESTED_GROUPS = 'ansible-resolve-nested-groups'

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

    void "max aliases with resolve nested groups on"() {
        given:
        setProjectConfig(PROJ_NAME, "resources.source.2.config.${RESOLVE_NESTED_GROUPS}", 'true')

        when: "the source is reloaded with the new setting"
        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,'.*')}
        final long deadlineNanos = System.nanoTime() + TimeUnit.MINUTES.toNanos(1)
        while (result.get(NODE_3)?.getAttributes()?.get('tags') != 'fr, fr2' && System.nanoTime() < deadlineNanos) {
            sleep(3000)
            result = client.apiCall {api-> api.listNodes(PROJ_NAME,'.*')}
        }

        then: "proxy-3 also gets the tag of its parent group fr"
        result.size() == 4
        result.get(NODE_1).getAttributes().get('tags') == 'fr, fr1'
        result.get(NODE_2).getAttributes().get('tags') == 'fr, fr1'
        result.get(NODE_3).getAttributes().get('tags') == 'fr, fr2'

        cleanup:
        setProjectConfig(PROJ_NAME, "resources.source.2.config.${RESOLVE_NESTED_GROUPS}", 'false')
    }
}
