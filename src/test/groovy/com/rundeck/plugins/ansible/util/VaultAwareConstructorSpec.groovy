package com.rundeck.plugins.ansible.util

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import spock.lang.Specification

class VaultAwareConstructorSpec extends Specification {

    private static Yaml yaml() {
        return new Yaml(new VaultAwareConstructor(new LoaderOptions()))
    }

    void "a !vault value is loaded as its encrypted text"() {
        given:
        String content = '''\
secret_access_key: !vault |
  $ANSIBLE_VAULT;1.1;AES256
  6162636465
plain: value
'''

        when:
        Map<String, Object> data = yaml().load(content)

        then:
        data.secret_access_key == '$ANSIBLE_VAULT;1.1;AES256\n6162636465\n'
        data.plain == 'value'
    }

    void "other unknown tags are still rejected"() {
        when:
        yaml().load('value: !!javax.script.ScriptEngineManager []')

        then:
        thrown(YAMLException)
    }
}
