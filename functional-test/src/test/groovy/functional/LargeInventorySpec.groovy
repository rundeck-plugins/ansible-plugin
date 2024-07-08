package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

@Testcontainers
class LargeInventorySpec  extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-large-inventory'

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME)
    }

    def "test large inventory"(){
        when:

        //wait for node to be available
        waitForNodes(PROJ_NAME)

        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,".*")}

        then:
        result!=null
        result.size()==8001
        result.get("Node-0")!=null
        result.get("Node-0").getAttributes().get("ansible_host") == "ssh-node"
        result.get("Node-0").getAttributes().get("ansible_ssh_user") == "rundeck"
        result.get("Node-0").getAttributes().get("some-var") == "1234"
        result.get("Node-7999")!=null
        result.get("Node-7999").getAttributes().get("ansible_host") == "ssh-node"
        result.get("Node-7999").getAttributes().get("ansible_ssh_user") == "rundeck"
        result.get("Node-7999").getAttributes().get("some-var") == "1234"
    }
}
