package com.rundeck.plugins.ansible.util;

import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * A utility class providing helper methods for working with YAML data.
 */
public class YamlUtil {

    public static final String ALIASES = "aliases";
    public static final String ALIAS_MATCH = "<<:";

    /**
     * Checks for aliases and anchors within the provided YAML content and returns a map of alias occurrences.
     *
     * @param content The YAML content string to analyze for aliases and anchors.
     * @return A map where keys are alias names and values are their corresponding counts within the content.
     */
    public static Map<String, Integer> checkAliasesAndAnchors(String content) {
        Map<String, Integer> resp = new HashMap<>();
        int total = countAliases(content);
        resp.put(ALIASES, total);
        return resp;
    }

    /**
     * Counts the number of aliases within the provided YAML content string.
     *
     * @param content The YAML content string to analyze for aliases.
     * @return The total number of aliases found in the content.
     */
    private static int countAliases(String content) {
        return StringUtils.countMatches(content, ALIAS_MATCH);
    }
}
