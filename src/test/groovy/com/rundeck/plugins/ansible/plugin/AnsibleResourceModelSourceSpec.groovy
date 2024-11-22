package com.rundeck.plugins.ansible.plugin

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeEntry
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.resources.ResourceModelSource
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree
import com.dtolabs.rundeck.core.utils.IPropertyLookup
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable
import com.rundeck.plugins.ansible.ansible.AnsibleInventoryList
import org.rundeck.app.spi.Services
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import spock.lang.Specification

import static com.rundeck.plugins.ansible.ansible.AnsibleInventoryList.AnsibleInventoryListBuilder

/**
 * AnsibleResourceModelSource test
 */
class AnsibleResourceModelSourceSpec extends Specification {

    void "get nodes gather facts false"() {
        given:
        Yaml yaml = new Yaml()
        String familyValue = 'Linux'
        String nameValue = 'RED HAT'
        String versionValue = "'7.9'"
        String archValue = 'x64'
        String usernameValue = 'test'
        String descValue = 'CentOS Linux'
        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        ResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when:
        Map<String, String> nodeData = [:]
        if (osFamily) { nodeData.put(osFamily, familyValue) }
        if (osName) { nodeData.put(osName, nameValue) }
        if (osVersion) { nodeData.put(osVersion, versionValue) }
        if (osArch) { nodeData.put(osArch, archValue) }
        if (username) { nodeData.put(username, usernameValue) }
        if (description) {
            nodeData.put(description, descValue)
        }

        def host = [(nodeName) : nodeData]
        def hosts = ['hosts' : host]
        def groups = ['ungrouped' : hosts]
        def children = ['children' : groups]
        def all = ['all' : children]

        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }

        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()

        then:
        nodes.size() == 1
        INodeEntry node = nodes[0]
        node.tags.size() == 1
        node.tags[0] == 'ungrouped'
        if (node.hostname)    { node.hostname == nodeName }
        if (node.nodename)    { node.nodename == nodeName }
        if (node.osFamily)    { node.osFamily == familyValue }
        if (node.osName)      { node.osName == nameValue }
        if (node.osVersion)   { node.osVersion == versionValue }
        if (node.osArch)      { node.osArch == archValue }
        if (node.username)    { node.username == usernameValue }
        if (node.description) { node.description == descValue }

        where:
        nodeName | osFamily            | osName            | osVersion        | osArch                 | username           | description
        'NODE_0' | 'osFamily'          | 'osName'          | 'osVersion'      | 'osArch'               | 'username'         | 'description'
        'NODE_1' | 'ansible_os_family' | 'ansible_os_name' | 'ansible_kernel' | 'ansible_architecture' | 'ansible_user'     | 'ansible_distribution'
        'NODE_2' | 'ansible_os_family' | 'ansible_os_name' | 'ansible_kernel' | 'ansible_architecture' | 'ansible_ssh_user' | 'ansible_distribution'
        'NODE_3' | 'ansible_os_family' | 'ansible_os_name' | 'ansible_kernel' | 'ansible_architecture' | 'ansible_user_id'  | 'ansible_distribution'
    }

    void "ansible yaml data size parameter without an Exception"() {
        given:
        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        ResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)
        plugin.yamlDataSize = 2

        when: "small inventory"
        AnsibleInventoryListBuilder inventoryListBuilder = mockInventoryList(qtyNodes)
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()

        then: "non exception is thrown because data can be handled"
        notThrown(YAMLException)
        nodes.size() == qtyNodes

        where:
        qtyNodes | _
        2_0000   | _
        3_0000   | _
    }

    void "ansible yaml data size parameter with an Exception"() {
        given:
        Framework framework = Mock(Framework) {
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        ResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)
        plugin.yamlDataSize = 2

        when: "huge inventory"
        AnsibleInventoryListBuilder inventoryListBuilder = mockInventoryList(100_000)
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        plugin.getNodes()

        then: "throws an exception because data is too big to be precessed"
        thrown(ResourceModelSourceException)
    }

    void "tag hosts is empty"() {
        given:
        Framework framework = Mock(Framework) {
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        ResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)
        plugin.yamlDataSize = 2

        when: "inventory with null hosts"
        AnsibleInventoryListBuilder inventoryListBuilder = mockInventoryList(1, true)
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        plugin.getNodes()

        then: "no exception thrown due to null tag is handled"
        notThrown(Exception)
    }

    private AnsibleInventoryListBuilder mockInventoryList(int qtyNodes, boolean nullHosts = false) {
        return Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> createNodes(qtyNodes, nullHosts)
            }
        }
    }

    private static String createNodes(int qty, boolean nullHosts) {
        Yaml yaml = new Yaml()
        Map<String, Object> host = [:]
        for (int i=1; i <= qty; i++) {
            String nodeName = "node-$i"
            String hostValue = "any-name-$i"
            host.put((nodeName), ['hostname' : (hostValue)])
        }
        def hosts = ['hosts' : nullHosts ? null : host]
        def groups = ['ungrouped' : hosts]
        def children = ['children' : groups]
        def all = ['all' : children]
        return yaml.dump(all)
    }

}
