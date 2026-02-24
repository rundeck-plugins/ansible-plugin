
package com.rundeck.plugins.ansible.logging

import com.dtolabs.rundeck.core.execution.workflow.OutputContext
import com.dtolabs.rundeck.core.logging.LogEventControl
import com.dtolabs.rundeck.core.logging.LogLevel
import com.dtolabs.rundeck.core.logging.PluginLoggingContext
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyValidator
import com.dtolabs.rundeck.core.plugins.configuration.ValidationException
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.descriptions.RenderingOption
import com.dtolabs.rundeck.plugins.descriptions.RenderingOptions
import com.dtolabs.rundeck.plugins.logging.LogFilterPlugin
import com.fasterxml.jackson.databind.ObjectMapper

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

import java.util.Iterator
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.GsonBuilder
import com.google.gson.Gson


/**
 * @author Simon Cateau
 * @since 20/09/2023
 */
@Plugin(name = "ansible-set_stats", service = "LogFilter")
@PluginDescription(title = "Ansible set_stats",
                   description = '''Parses the output of the ansible set_stats module and generates the corresponding data context variables.\n\n
To display the output of the set_stats module, you must set show_custom_stats in section [defaults] in ansible.cfg or by defining environment variable ANSIBLE_SHOW_CUSTOM_STATS to true.

See the [official Ansible documentation](https://docs.ansible.com/ansible/latest/collections/ansible/builtin/set_stats_module.html).
''')

class AnsibleSetStatsFilterPlugin implements LogFilterPlugin{
    @PluginProperty(
        title = "Log Data",
        description = "If true, log the captured data",
        defaultValue = 'false'
    )
    Boolean outputData

    Pattern setStatsGlobalPattern
    OutputContext outputContext
    Map<String, String> allData
    ObjectMapper mapper

    @Override
    void init(final PluginLoggingContext context) {
        String regex = /^\tRUN:\s(\{.*\})$/
        setStatsGlobalPattern = Pattern.compile(regex)
        outputContext = context.getOutputContext()
        mapper = new ObjectMapper()
        allData = [:]
    }

    @Override
    void handleEvent(final PluginLoggingContext context, final LogEventControl event) {
        if (event.eventType == 'log' && event.loglevel == LogLevel.NORMAL && event.message?.length() > 0) {
            Matcher match = setStatsGlobalPattern.matcher(event.message)

            if(match.matches()){
                String jsonString = match.group(1)
                JsonObject obj = JsonParser.parseString(jsonString).getAsJsonObject()
                Iterator<String> keys = obj.keySet().iterator()
                Gson gson = new GsonBuilder().create()
                while(keys.hasNext()) {
                        String key = keys.next()
                        String value = gson.toJson(obj.get(key))
                        allData[key] = value
                        outputContext.addOutput("data", key, value)
                }
            }
        }
    }

    @Override
    void complete(final PluginLoggingContext context) {
        if (allData) {
            if (outputData) {
                context.log(
                        2,
                        mapper.writeValueAsString(allData),
                        [
                                'content-data-type'       : 'application/json',
                                'content-meta:table-title': 'Ansible set_stats: Results'
                        ]
                )
            }
            // allData = [:]
        }
    }
}
