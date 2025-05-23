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
        result.get("Node-0").getAttributes().get("ansible_host") == "ssh-node"
        result.get("Node-0").getAttributes().get("ansible_ssh_user") == "rundeck"
        result.get("Node-0").getAttributes().get("some-var") == "1234"
        result.get("Node-7999")!=null
        result.get("Node-7999").getAttributes().get("ansible_host") == "ssh-node"
        result.get("Node-7999").getAttributes().get("ansible_ssh_user") == "rundeck"
        result.get("Node-7999").getAttributes().get("some-var") == "1234"
    }

    def "test empty inventory path"(){
        when:

        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,"tags:fake")}

        then:
        result!=null
        result.size()==35
        result.get("node1")!=null
        result.get("node1").getAttributes().get("ansible_host") == "node1"
        result.get("node1").getAttributes().get("ansible_user") == "agent"
        result.get("node1").getAttributes().get("ansible_port") == "22"
    }
}
