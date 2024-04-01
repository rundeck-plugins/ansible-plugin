package com.rundeck.plugins.ansible.ansible

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.rundeck.plugins.ansible.plugin.AnsiblePluginGroup
import spock.lang.Specification
import com.dtolabs.rundeck.core.execution.ExecutionLogger

class AnsibleRunnerContextBuilderSpec extends Specification {

    def "test plugin group"(){
        given:

        PluginStepContext context = Mock(PluginStepContext){
            getDataContext() >> ['job': ['loglevel':'INFO']]
            getExecutionContext() >> Mock(ExecutionContext){
                getDataContext() >> [:]
                getExecutionLogger() >> Mock(ExecutionLogger)
            }
            getFramework() >> Mock(Framework)
            getNodes() >> Mock(INodeSet){
                getNodes() >> []
            }
        }

        Map<String, Object> configuration = [
                'ansible-playbook' : 'path/to/playbook'
        ]

        AnsiblePluginGroup pluginGroup = new AnsiblePluginGroup()
        pluginGroup.setAnsibleConfigFilePath("/etc/ansible/ansible.cfg")
        pluginGroup.setEncryptExtraVars(true)
        pluginGroup.setAnsibleBinariesDirPath("/usr/local/lib")

        when:
        AnsibleRunnerContextBuilder contextBuilder = new AnsibleRunnerContextBuilder(context.getExecutionContext(),
                context.getFramework(),
                context.getNodes(),
                configuration,
                pluginGroup)

        then:
        contextBuilder.getConfigFile() == "/etc/ansible/ansible.cfg"
        contextBuilder.getBinariesFilePath() == "/usr/local/lib"
        contextBuilder.encryptExtraVars()
        contextBuilder.getPlaybookPath() == "path/to/playbook"
    }

    def "test plugin group not set"(){
        given:

        PluginStepContext context = Mock(PluginStepContext){
            getDataContext() >> ['job': ['loglevel':'INFO']]
            getExecutionContext() >> Mock(ExecutionContext){
                getDataContext() >> [:]
            }
            getFramework() >> Mock(Framework)
            getNodes() >> Mock(INodeSet){
                getNodes() >> []
            }
        }

        Map<String, Object> configuration = [
                'ansible-playbook' : 'path/to/playbook',
                'ansible-config-file-path': '/etc/ansible/ansible.cfg'
        ]

        when:
        AnsibleRunnerContextBuilder contextBuilder = new AnsibleRunnerContextBuilder(context.getExecutionContext(),
                context.getFramework(),
                context.getNodes(),
                configuration,
                null)

        then:
        contextBuilder.getConfigFile() == "/etc/ansible/ansible.cfg"
        contextBuilder.getBinariesFilePath() == null
        !contextBuilder.encryptExtraVars()
        contextBuilder.getPlaybookPath() == "path/to/playbook"
    }
}
