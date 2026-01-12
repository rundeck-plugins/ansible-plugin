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
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
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
    void "structured data like ports is serialized as JSON when gather facts is false"() {
        given:
        def nodeName = 'NODE_JSON'
        def structuredPorts = [
                [port: 22, protocol: 'tcp', service: 'ssh', state: 'open'],
                [port: 80, protocol: 'tcp', service: 'http', state: 'open']
        ]

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
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'true')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        def host = [(nodeName): [
                'ports': structuredPorts
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]

        Yaml yaml = new Yaml()
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }

        plugin.ansibleInventoryListBuilder = inventoryListBuilder

        when:
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then:
        node != null
        node.getAttributes().containsKey("ports")

        and:
        def portsJson = node.getAttributes().get("ports")
        portsJson.startsWith("[")
        portsJson.contains("\"port\":22")
        portsJson.contains("\"service\":\"ssh\"")
    }


    void "tag hosts is empty"() {
        given:
        Framework framework = Mock(Framework) {
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
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

    void "processHosts should process valid String host keys without errors"() {
        given: "a plugin with standard inventory"
        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "YAML contains valid String keys"
        // Note: Testing non-String keys directly is difficult because Java's type system
        // prevents creating Map<String, Object> with non-String keys. The defensive code
        // handles the rare edge case where complex YAML anchor/alias patterns produce non-String keys during parsing.
        Yaml yaml = new Yaml()
        def validHost = ['node-1' : ['hostname' : 'host-1']]
        def hosts = ['hosts' : validHost]
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

        then: "valid String keys are processed successfully"
        nodes.size() == 1
        nodes.getNodeNames().contains('node-1')
    }

    void "processHosts should filter out JSON object keys"() {
        given: "a plugin configured"
        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "YAML contains a key that is a valid JSON object (serialized data)"
        Yaml yaml = new Yaml()
        def hosts = [
            'valid-node': ['hostname': 'valid-host'],
            '{"inventory":"data","nested":{"key":"value"}}': ['hostname': 'json-object-key'],
            '{simple-curly-name}': ['hostname': 'not-json-host']  // Not valid JSON, should be kept
        ]
        def hostsMap = ['hosts': hosts]
        def groups = ['ungrouped': hostsMap]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()

        then: "JSON object key is filtered out but valid nodes remain"
        nodes.size() == 2
        nodes.getNodeNames().contains('valid-node')
        nodes.getNodeNames().contains('{simple-curly-name}')  // Not valid JSON, so kept
        !nodes.getNodeNames().contains('{"inventory":"data","nested":{"key":"value"}}')
    }

    void "processHosts should handle valid nodes without exception"() {
        given: "a plugin configured"
        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        plugin.configure(config)
        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "processing hosts through the normal flow"
        Yaml yaml = new Yaml()
        def hosts = ['valid-node': ['hostname': 'valid-host']]
        def hostsMap = ['hosts': hosts]
        def groups = ['ungrouped': hostsMap]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()

        then: "no exception thrown and node is processed correctly"
        notThrown(Exception)
        nodes.size() == 1
        nodes.getNodeNames().contains('valid-node')
    }

    void "ignore host vars with prefix should filter variables when gather facts is false"() {
        given: "a plugin configured with importInventoryVars and ignoreInventoryVars"
        def nodeName = 'testhost'
        def certificate = """-----BEGIN CERTIFICATE-----
fake-cert
-----END CERTIFICATE-----"""

        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'true')
        config.put(AnsibleDescribable.ANSIBLE_IGNORE_INVENTORY_VARS, 'ccp_certificate_chain')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "inventory contains host vars including one that should be ignored"
        Yaml yaml = new Yaml()
        def host = [(nodeName): [
                'ansible_connection': 'local',
                'ccp_certificate_chain': certificate,
                'host_visible_var': 'visible_value',
                'normal_group_var': 'normal_value',
                'vault_api_token': 'super_secret_token'
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then: "ignored variable should not be present in node attributes"
        node != null
        !node.getAttributes().containsKey('ccp_certificate_chain')

        and: "other variables should be imported when importInventoryVars is true"
        node.getAttributes().containsKey('host_visible_var')
        node.getAttributes().get('host_visible_var') == 'visible_value'
        node.getAttributes().containsKey('normal_group_var')
        node.getAttributes().get('normal_group_var') == 'normal_value'
        node.getAttributes().containsKey('vault_api_token')
        node.getAttributes().get('vault_api_token') == 'super_secret_token'

        and: "ansible special vars should be filtered"
        !node.getAttributes().containsKey('ansible_connection')
    }

    void "should not import inventory vars when importInventoryVars is false"() {
        given: "a plugin configured with importInventoryVars disabled"
        def nodeName = 'testhost'

        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'false')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "inventory contains host vars"
        Yaml yaml = new Yaml()
        def host = [(nodeName): [
                'ansible_connection': 'local',
                'host_var_1': 'value1',
                'host_var_2': 'value2'
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then: "no host variables should be imported as node attributes"
        node != null
        !node.getAttributes().containsKey('ansible_connection')
        !node.getAttributes().containsKey('host_var_1')
        !node.getAttributes().containsKey('host_var_2')
    }

    void "should filter multiple comma-separated prefixes"() {
        given: "a plugin configured with multiple ignore prefixes"
        def nodeName = 'testhost'

        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'true')
        config.put(AnsibleDescribable.ANSIBLE_IGNORE_INVENTORY_VARS, 'secret_,private_,internal_')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "inventory contains vars with different prefixes"
        Yaml yaml = new Yaml()
        def host = [(nodeName): [
                'secret_key': 'should_be_filtered',
                'private_data': 'should_be_filtered',
                'internal_config': 'should_be_filtered',
                'public_var': 'should_be_visible',
                'normal_var': 'should_be_visible'
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then: "only non-filtered variables should be present"
        node != null
        !node.getAttributes().containsKey('secret_key')
        !node.getAttributes().containsKey('private_data')
        !node.getAttributes().containsKey('internal_config')
        node.getAttributes().containsKey('public_var')
        node.getAttributes().get('public_var') == 'should_be_visible'
        node.getAttributes().containsKey('normal_var')
        node.getAttributes().get('normal_var') == 'should_be_visible'
    }

    void "should handle ignore list with spaces and empty entries"() {
        given: "a plugin configured with messy ignore prefix list"
        def nodeName = 'testhost'

        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'true')
        // Messy input with spaces, empty entries
        config.put(AnsibleDescribable.ANSIBLE_IGNORE_INVENTORY_VARS, ' secret_ , , private_ ,  ')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "inventory contains vars"
        Yaml yaml = new Yaml()
        def host = [(nodeName): [
                'secret_key': 'should_be_filtered',
                'private_data': 'should_be_filtered',
                'normal_var': 'should_be_visible'
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then: "filtering should work correctly despite messy input"
        node != null
        !node.getAttributes().containsKey('secret_key')
        !node.getAttributes().containsKey('private_data')
        node.getAttributes().containsKey('normal_var')
        node.getAttributes().get('normal_var') == 'should_be_visible'
    }

    void "should filter all Ansible special variables"() {
        given: "a plugin configured with importInventoryVars enabled"
        def nodeName = 'testhost'

        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'true')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "inventory contains Ansible special variables"
        Yaml yaml = new Yaml()
        def host = [(nodeName): [
                'ansible_connection': 'local',
                'ansible_python_interpreter': '/usr/bin/python3',
                'discovered_interpreter_python': '/usr/bin/python3',
                'group_names': ['web', 'prod'],
                'groups': ['all': ['host1']],
                'hostvars': ['host1': ['var1': 'value1']],
                'inventory_hostname': 'testhost',
                'playbook_dir': '/etc/ansible',
                'role_path': '/etc/ansible/roles',
                'normal_var': 'should_be_visible'
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then: "all Ansible special variables should be filtered"
        node != null
        !node.getAttributes().containsKey('ansible_connection')
        !node.getAttributes().containsKey('ansible_python_interpreter')
        !node.getAttributes().containsKey('discovered_interpreter_python')
        !node.getAttributes().containsKey('group_names')
        !node.getAttributes().containsKey('groups')
        !node.getAttributes().containsKey('hostvars')
        !node.getAttributes().containsKey('inventory_hostname')
        !node.getAttributes().containsKey('playbook_dir')
        !node.getAttributes().containsKey('role_path')

        and: "normal variables should still be imported"
        node.getAttributes().containsKey('normal_var')
        node.getAttributes().get('normal_var') == 'should_be_visible'
    }

    void "should not duplicate attributes already set by applyNodeTags"() {
        given: "a plugin configured with importInventoryVars enabled"
        def nodeName = 'testhost'

        Framework framework = Mock(Framework) {
            getPropertyLookup() >> Mock(IPropertyLookup){
                getProperty("framework.tmp.dir") >> '/tmp'
            }
            getBaseDir() >> Mock(File) {
                getAbsolutePath() >> '/tmp'
            }
        }
        AnsibleResourceModelSource plugin = new AnsibleResourceModelSource(framework)
        Properties config = new Properties()
        config.put('project', 'project_1')
        config.put(AnsibleDescribable.ANSIBLE_GATHER_FACTS, 'false')
        config.put(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS, 'true')
        plugin.configure(config)

        Services services = Mock(Services) {
            getService(KeyStorageTree.class) >> Mock(KeyStorageTree)
        }
        plugin.setServices(services)

        when: "inventory contains variables used by applyNodeTags"
        Yaml yaml = new Yaml()
        def host = [(nodeName): [
                'ansible_host': '192.168.1.100',
                'ansible_user': 'ubuntu',
                'ansible_os_family': 'Debian',
                'custom_var': 'custom_value'
        ]]
        def hosts = ['hosts': host]
        def groups = ['ungrouped': hosts]
        def children = ['children': groups]
        def all = ['all': children]
        String result = yaml.dump(all)

        AnsibleInventoryListBuilder inventoryListBuilder = Mock(AnsibleInventoryListBuilder) {
            build() >> Mock(AnsibleInventoryList) {
                getNodeList() >> result
            }
        }
        plugin.ansibleInventoryListBuilder = inventoryListBuilder
        INodeSet nodes = plugin.getNodes()
        INodeEntry node = nodes.getNode(nodeName)

        then: "node properties should be set from ansible variables"
        node != null
        node.getHostname() == '192.168.1.100'
        node.getUsername() == 'ubuntu'
        node.getOsFamily() == 'Debian'

        and: "ansible variables should NOT be in attributes (filtered by ansible_ prefix)"
        !node.getAttributes().containsKey('ansible_host')
        !node.getAttributes().containsKey('ansible_user')
        !node.getAttributes().containsKey('ansible_os_family')

        and: "custom variables should be in attributes"
        node.getAttributes().containsKey('custom_var')
        node.getAttributes().get('custom_var') == 'custom_value'
    }

}
