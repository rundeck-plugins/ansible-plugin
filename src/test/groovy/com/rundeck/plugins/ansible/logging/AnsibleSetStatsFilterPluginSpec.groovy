package com.rundeck.plugins.ansible.logging

import com.dtolabs.rundeck.core.execution.workflow.SharedOutputContext
import com.dtolabs.rundeck.core.logging.LogEventControl
import com.dtolabs.rundeck.core.logging.LogLevel
import com.dtolabs.rundeck.core.logging.PluginLoggingContext
import spock.lang.Specification
import spock.lang.Unroll

class AnsibleSetStatsFilterPluginSpec extends Specification {

    SharedOutputContext outputContext
    PluginLoggingContext context
    AnsibleSetStatsFilterPlugin plugin

    def setup() {
        outputContext = Mock(SharedOutputContext)
        context = Mock(PluginLoggingContext) {
            getOutputContext() >> outputContext
        }
        plugin = new AnsibleSetStatsFilterPlugin()
        plugin.init(context)
    }

    def event(String message) {
        return Mock(LogEventControl) {
            getEventType() >> 'log'
            getLoglevel() >> LogLevel.NORMAL
            getMessage() >> message
        }
    }

    @Unroll
    def "preserves the unquoted scalar value for a #description"() {
        given:
        String line = "\tRUN: {\"${key}\": ${jsonValue}}"

        when:
        plugin.handleEvent(context, event(line))

        then:
        1 * outputContext.addOutput("data", key, expected)

        where:
        description | key      | jsonValue    | expected
        "string"    | "status" | '"success"'  | "success"
        "integer"   | "count"  | '42'         | "42"
        "boolean"   | "ready"  | 'true'       | "true"
    }

    def "serializes a nested object instead of failing"() {
        given:
        String line = '\tRUN: {"host_facts": {"os": "linux", "cpus": 4}}'

        when:
        plugin.handleEvent(context, event(line))

        then:
        1 * outputContext.addOutput("data", "host_facts", '{"os":"linux","cpus":4}')
    }

    def "serializes a nested array instead of failing"() {
        given:
        String line = '\tRUN: {"tags": ["a", "b"]}'

        when:
        plugin.handleEvent(context, event(line))

        then:
        1 * outputContext.addOutput("data", "tags", '["a","b"]')
    }

    def "serializes a null value instead of failing"() {
        given:
        String line = '\tRUN: {"missing": null}'

        when:
        plugin.handleEvent(context, event(line))

        then:
        1 * outputContext.addOutput("data", "missing", 'null')
    }
}
