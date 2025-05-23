package functional

import functional.base.BaseTestConfiguration
import org.testcontainers.spock.Testcontainers

@Testcontainers
class EncryptedInventorySpec extends BaseTestConfiguration {

    static String PROJ_NAME = 'ansible-encrypted-inventory'
    static String DEFAULT_NODE_NAME = "ssh-node"

    def setupSpec() {
        startCompose()
        configureRundeck(PROJ_NAME, DEFAULT_NODE_NAME)
    }

    def "test encrypted inventory"(){
        when:

        //wait for node to be available
        def result = client.apiCall {api-> api.listNodes(PROJ_NAME,".*")}

        then:
        result!=null
        result.size()==4
        result.get("ssh-node")!=null
        result.get("ssh-node-1")!=null
        result.get("ssh-node-2")!=null
    }


}
