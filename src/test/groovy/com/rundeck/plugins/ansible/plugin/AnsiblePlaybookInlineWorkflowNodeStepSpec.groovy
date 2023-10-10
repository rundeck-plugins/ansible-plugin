package com.rundeck.plugins.ansible.plugin

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeEntry
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import org.junit.jupiter.api.Assertions
import spock.lang.Specification


class AnsiblePlaybookInlineWorkflowNodeStepSpec extends Specification{

    void "failure data should not include null values when throwing"(){
        given:
        NodeStepPlugin plugin = new AnsiblePlaybookInlineWorkflowNodeStep()
        INodeEntry node = Mock(INodeEntry){
            getNodename() >> 'localhost'
        }

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
                'ansible-playbook' : 'path/to/playbook'
        ]

        when:
        plugin.executeNodeStep(context, configuration, node)

        then:
        NodeStepException e = thrown(NodeStepException)
        e.getFailureData().each { String key, Object value ->
            Assertions.assertNotNull(value, "Value for key ${key} should not be null")
        }
    }
}
