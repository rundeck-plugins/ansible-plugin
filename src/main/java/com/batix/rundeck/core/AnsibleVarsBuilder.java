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
        appendProps(builder, "node");
        appendProps(builder, "option");
        return builder.toString();
    }

    private void appendProps(StringBuilder builder, String prefix) {
        Map<String, String> properties = dataContext.get(prefix);
        if (properties != null) {
            for (String variable : properties.keySet()) {
                String value = properties.get(variable);
                String ansibleVar = String.format("%s_%s: \"%s\"", prefix, variable.replaceAll("-", "_"), value);
                builder.append(ansibleVar);
                builder.append(System.getProperty("line.separator"));
            }
        }
    }

}
