package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

/**
 * Gather Facts node source with inventory vars that ansible-core 2.19+ can't render:
 * a var referencing hostvars, and undefined values at the top level or nested in a
 * dict or list. The node source must still load every host.
 */
@Testcontainers
class GatherFactsTemplatingSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-gather-facts-templating'
    static String NODE_1 = 'templating-1.example.net'
    static String NODE_2 = 'templating-2.example.net'

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, NODE_1)
    }

    void "gather facts loads hosts whose vars can't all be rendered"() {
        when:
        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,'.*')}

        then: "both hosts load with their gathered facts"
        result != null
        result.get(NODE_1)?.getAttributes()?.get('lsb_codename') != null
        result.get(NODE_2)?.getAttributes()?.get('lsb_codename') != null
    }
}
