package com.rundeck.plugins.ansible.ansible

import com.dtolabs.rundeck.core.common.NodeEntryImpl
import spock.lang.Specification
import spock.lang.Unroll

/**
 * InventoryList NodeTag test, covering the static-inventory (gather_facts=false)
 * osFamily/osName normalization added for RUN-4821.
 */
class InventoryListSpec extends Specification {

    @Unroll
    void "OS_FAMILY handle trusts explicit osFamily tag as-is: #osFamilyValue"() {
        given:
        NodeEntryImpl node = new NodeEntryImpl()
        Map<String, Object> tags = new HashMap<>()
        tags.put("osFamily", osFamilyValue)
        tags.put("ansible_os_family", "Debian")

        when:
        InventoryList.NodeTag.OS_FAMILY.handle(node, tags)

        then:
        node.getOsFamily() == osFamilyValue

        where:
        osFamilyValue << ["unix", "windows", "some-custom-value"]
    }

    @Unroll
    void "OS_FAMILY handle normalizes ansible_os_family when no explicit osFamily tag: #ansibleOsFamily -> #expected"() {
        given:
        NodeEntryImpl node = new NodeEntryImpl()
        Map<String, Object> tags = new HashMap<>()
        tags.put("ansible_os_family", ansibleOsFamily)

        when:
        InventoryList.NodeTag.OS_FAMILY.handle(node, tags)

        then:
        node.getOsFamily() == expected

        where:
        ansibleOsFamily | expected
        "Debian"        | "unix"
        "RedHat"        | "unix"
        "Windows"       | "windows"
    }

    void "OS_FAMILY handle leaves osFamily unset when neither tag is present"() {
        given:
        NodeEntryImpl node = new NodeEntryImpl()
        Map<String, Object> tags = new HashMap<>()

        when:
        InventoryList.NodeTag.OS_FAMILY.handle(node, tags)

        then:
        node.getOsFamily() == null
    }

    void "OS_NAME handle prefers explicit osName tag"() {
        given:
        NodeEntryImpl node = new NodeEntryImpl()
        Map<String, Object> tags = new HashMap<>()
        tags.put("osName", "my-os-name")
        tags.put("ansible_os_name", "ansible-os-name")
        tags.put("ansible_os_family", "Debian")

        when:
        InventoryList.NodeTag.OS_NAME.handle(node, tags)

        then:
        node.getOsName() == "my-os-name"
    }

    void "OS_NAME handle falls back to raw ansible_os_family distro when no osName/ansible_os_name present"() {
        given:
        NodeEntryImpl node = new NodeEntryImpl()
        Map<String, Object> tags = new HashMap<>()
        tags.put("ansible_os_family", "Debian")

        when:
        InventoryList.NodeTag.OS_NAME.handle(node, tags)

        then:
        node.getOsName() == "Debian"
    }

    void "OS_NAME handle leaves osName unset when no name tags are present"() {
        given:
        NodeEntryImpl node = new NodeEntryImpl()
        Map<String, Object> tags = new HashMap<>()

        when:
        InventoryList.NodeTag.OS_NAME.handle(node, tags)

        then:
        node.getOsName() == null
    }
}
