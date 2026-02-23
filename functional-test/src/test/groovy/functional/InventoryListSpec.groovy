package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

@Testcontainers
class InventoryListSpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-large-inventory'

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, "Node-0")
    }

    def "test large inventory"(){
        when:

        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,"tags:test")}

        then:
        result!=null
        result.size()==8000
        result.get("Node-0")!=null
        // ansible_ prefixed variables should be filtered out, not in attributes
        result.get("Node-0").getAttributes().get("ansible_host") == null
        result.get("Node-0").getAttributes().get("ansible_ssh_user") == null
        // custom variables should still be imported
        result.get("Node-0").getAttributes().get("some-var") == "1234"
        result.get("Node-7999")!=null
        result.get("Node-7999").getAttributes().get("ansible_host") == null
        result.get("Node-7999").getAttributes().get("ansible_ssh_user") == null
        result.get("Node-7999").getAttributes().get("some-var") == "1234"
    }

    def "test empty inventory path"(){
        when:

        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,"tags:fake")}

        then:
        result!=null
        result.size()==35
        result.get("node1")!=null
        // ansible_ prefixed variables should be filtered out, not in attributes
        result.get("node1").getAttributes().get("ansible_host") == null
        result.get("node1").getAttributes().get("ansible_user") == null
        result.get("node1").getAttributes().get("ansible_port") == null
    }
}
