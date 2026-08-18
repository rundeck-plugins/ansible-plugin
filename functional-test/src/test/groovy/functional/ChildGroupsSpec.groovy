package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

@Testcontainers
class ChildGroupsSpec extends BaseTestConfiguration {

    static String NODENAME  = 'nodename'
    static String HOSTNAME  = 'hostname'
    static String TAGS      = 'tags'
    static String PROJ_NAME = 'ansible-child-groups'
    static String NODE_1    = 'one.example.com'
    static String TAGS_1    = 'dbservers, east, prod'
    static String NODE_2    = 'three.example.com'
    static String TAGS_2    = 'dbservers, test, west'
    static String NODE_3    = 'mail.example.com'
    static String TAGS_3    = 'ungrouped'

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, NODE_1)
    }

    void "child groups"() {
        when:
        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,'.*')}

        then:
        result != null
        result.size() == 7
        result.get(NODE_1) != null
        result.get(NODE_1).getAttributes().get(NODENAME) == NODE_1
        result.get(NODE_1).getAttributes().get(HOSTNAME) == NODE_1
        result.get(NODE_1).getAttributes().get(TAGS)     == TAGS_1
        result.get(NODE_2) != null
        result.get(NODE_2).getAttributes().get(NODENAME) == NODE_2
        result.get(NODE_2).getAttributes().get(HOSTNAME) == NODE_2
        result.get(NODE_2).getAttributes().get(TAGS)     == TAGS_2
        result.get(NODE_3) != null
        result.get(NODE_3).getAttributes().get(NODENAME) == NODE_3
        result.get(NODE_3).getAttributes().get(HOSTNAME) == NODE_3
        result.get(NODE_3).getAttributes().get(TAGS)     == TAGS_3
    }
}
