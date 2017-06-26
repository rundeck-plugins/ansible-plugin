package com.batix.rundeck.core;

import java.util.Map;

/**
 * Created by vincenzo.pirrone on 13/02/2017.
 */
public class AnsibleVarsBuilder {

    private final Map<String, Map<String, String>> dataContext;

    public AnsibleVarsBuilder(Map<String, Map<String, String>> dataContext) {
        this.dataContext = dataContext;
    }

    public String buildVars() {
        StringBuilder builder = new StringBuilder();
        for (String prefix : dataContext.keySet()) {
            appendProps(builder, prefix);
        }
        return builder.toString();
    }

    private void appendProps(StringBuilder builder, String prefix) {
        Map<String, String> properties = dataContext.get(prefix);
        if (properties != null) {
            builder.append(String.format("%s:\n", prefix));
            for (String variable : properties.keySet()) {
                String value = properties.get(variable);
                String ansibleVar = String.format("  %s: \"%s\"\n", variable.replaceAll("[.-]", "_"), value);
                builder.append(ansibleVar);
            }
        }
    }

}
