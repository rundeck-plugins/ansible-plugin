package com.rundeck.plugins.ansible.plugin

import com.dtolabs.rundeck.core.plugins.configuration.Description
import com.dtolabs.rundeck.core.plugins.configuration.Validator
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable
import spock.lang.Specification

class AnsibleFileCopierSpec extends Specification {

    def "description declares the ansible-binaries-dir-path property"() {
        given:
        Description desc = new AnsibleFileCopier().getDescription()

        expect:
        desc.properties.find { it.name == AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH } != null
    }

    def "description maps ansible-binaries-dir-path to the project.* and framework.* keys"() {
        given:
        Description desc = new AnsibleFileCopier().getDescription()

        expect:
        desc.propertiesMapping[AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH] ==
                AnsibleDescribable.PROJ_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH
        desc.fwkPropertiesMapping[AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH] ==
                AnsibleDescribable.FWK_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH
    }

    def "round-trip: saving the property prefixes it with project. and loading recovers the value"() {
        given:
        Description desc = new AnsibleFileCopier().getDescription()
        Map<String, String> uiConfig = [
                (AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH): '/usr/local/bin/'
        ]

        when: "user-entered config is mapped to project property keys (save flow)"
        Map<String, String> stored = Validator.mapProperties(uiConfig, desc)

        then: "value is persisted under project.ansible-binaries-dir-path"
        stored[AnsibleDescribable.PROJ_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH] == '/usr/local/bin/'
        !stored.containsKey(AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH)

        when: "project properties are demapped back to UI config (load flow)"
        Map<String, String> uiBack = Validator.demapProperties(stored, desc)

        then: "value is restored under the plugin property name"
        uiBack[AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH] == '/usr/local/bin/'
    }

    def "regression: previously orphaned key without project prefix is ignored on load"() {
        given: "a project.properties left over by the pre-fix bug, where the key was persisted without prefix"
        Description desc = new AnsibleFileCopier().getDescription()
        Map<String, String> orphaned = [
                (AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH): '/orphaned/value'
        ]

        when:
        Map<String, String> uiBack = Validator.demapProperties(orphaned, desc)

        then: "orphaned entry is not surfaced to the UI"
        !uiBack.containsKey(AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH)
    }
}
