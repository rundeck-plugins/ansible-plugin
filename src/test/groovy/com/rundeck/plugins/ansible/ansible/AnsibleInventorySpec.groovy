package com.rundeck.plugins.ansible.ansible

import com.google.gson.Gson
import spock.lang.Specification

/**
 * AnsibleInventory test
 */
class AnsibleInventorySpec extends Specification {

    void "addHost should sanitize group names with invalid characters"() {
        given: "an AnsibleInventory instance"
        AnsibleInventory inventory = new AnsibleInventory()
        Gson gson = new Gson()

        when: "adding a host with tags containing colons"
        Map<String, String> attributes = new HashMap<>()
        attributes.put("tags", "runner:tag:ansible,testnodes")
        inventory.addHost("test-node", "127.0.0.1", attributes)

        then: "group names should have colons replaced with underscores"
        String json = gson.toJson(inventory)
        json.contains("runner_tag_ansible")
        json.contains("testnodes")
        !json.contains("runner:tag:ansible")
    }

    void "addHost should sanitize group names with special characters"() {
        given: "an AnsibleInventory instance"
        AnsibleInventory inventory = new AnsibleInventory()
        Gson gson = new Gson()

        when: "adding a host with tags containing various special characters"
        Map<String, String> attributes = new HashMap<>()
        attributes.put("tags", "tag@with#special!chars,normal-tag_ok")
        inventory.addHost("test-node", "127.0.0.1", attributes)

        then: "special characters should be replaced with underscores"
        String json = gson.toJson(inventory)
        json.contains("tag_with_special_chars")
        json.contains("normal-tag_ok")
    }

    void "addHost should remove reserved tags attribute from host variables"() {
        given: "an AnsibleInventory instance"
        AnsibleInventory inventory = new AnsibleInventory()
        Gson gson = new Gson()

        when: "adding a host with tags attribute"
        Map<String, String> attributes = new HashMap<>()
        attributes.put("tags", "tag1,tag2")
        attributes.put("custom_attr", "value")
        inventory.addHost("test-node", "127.0.0.1", attributes)

        then: "tags should not appear in host variables but custom attributes should"
        String json = gson.toJson(inventory)
        json.contains("custom_attr")
        json.contains("value")
        // Verify tags is not in the host's attributes (not in quoted key context)
        !json.contains('"tags"')
    }

    void "addHost should remove reserved Ansible variables"() {
        given: "an AnsibleInventory instance"
        AnsibleInventory inventory = new AnsibleInventory()
        Gson gson = new Gson()

        when: "adding a host with reserved Ansible variables"
        Map<String, String> attributes = new HashMap<>()
        attributes.put("hostvars", "should_be_removed")
        attributes.put("group_names", "should_be_removed")
        attributes.put("groups", "should_be_removed")
        attributes.put("environment", "should_be_removed")
        attributes.put("custom_var", "should_remain")
        inventory.addHost("test-node", "127.0.0.1", attributes)

        then: "reserved variables should be removed"
        String json = gson.toJson(inventory)
        json.contains("custom_var")
        json.contains("should_remain")
        !json.contains("hostvars")
        !json.contains("group_names")
        !json.contains("should_be_removed")
    }

    void "addHost should create groups from osFamily attribute and retain it in host variables"() {
        given: "an AnsibleInventory instance"
        AnsibleInventory inventory = new AnsibleInventory()
        Gson gson = new Gson()

        when: "adding a host with osFamily attribute"
        Map<String, String> attributes = new HashMap<>()
        attributes.put("osFamily", "unix")
        attributes.put("custom_attr", "test_value")
        inventory.addHost("test-node", "127.0.0.1", attributes)

        then: "unix group should be created AND osFamily should remain in host variables"
        String json = gson.toJson(inventory)
        json.contains("unix")
        // osFamily should NOT be removed (unlike tags) - it's needed by other components
        json.contains('"osFamily":"unix"')
        json.contains("custom_attr")
    }

    void "addHost should handle multiple tags and create multiple groups"() {
        given: "an AnsibleInventory instance"
        AnsibleInventory inventory = new AnsibleInventory()
        Gson gson = new Gson()

        when: "adding a host with multiple tags"
        Map<String, String> attributes = new HashMap<>()
        attributes.put("tags", "linux,web-server,production")
        inventory.addHost("test-node", "127.0.0.1", attributes)

        then: "all groups should be created"
        String json = gson.toJson(inventory)
        json.contains("linux")
        json.contains("web-server")
        json.contains("production")
    }
}
