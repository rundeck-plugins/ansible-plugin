package com.rundeck.plugins.ansible.ansible;

import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

@Data
public class InventoryList {

    public static final String ALL = "all";
    public static final String CHILDREN = "children";
    public static final String HOSTS = "hosts";
    public static final String ERROR_MISSING_FIELD = "Error: Missing tag '%s'";
    public static final String ERROR_MISSING_TAG = "Error: Not tag found among these searched: '%s'";

    /**
     * Gets value from a tag from Ansible inventory
     * @param tag tag from yaml output
     * @param field field to look for
     * @return value from tag yaml output
     * @param <T> type you want to receive
     */
    public static <T> T getValue(Map<String, Object> tag, final String field) {
        Object obj = null;
        if (Optional.ofNullable(tag.get(field)).isPresent()) {
            obj = tag.get(field);
        }
        return getType(obj);
    }

    /**
     * Casts an object you want to receive
     * @param obj generic object
     * @return cast object
     * @param <T> type you want to receive
     */
    @SuppressWarnings("unchecked")
    public static <T> T getType(Object obj) {
        return (T) obj;
    }

    /**
     * Process an Ansible tag
     * @param nodeTag Ansible node tag
     * @param node Rundeck node to build
     * @param tags tags to evaluate
     * @throws ResourceModelSourceException
     */
    public static void tagHandle(NodeTag nodeTag, NodeEntryImpl node, Map<String, Object> tags)
            throws ResourceModelSourceException {
        nodeTag.handle(node, tags);
    }

    /**
     * Finds a tag if it exists in a map of tags
     * @param nameTags map of tags to find
     * @param tags tags from Ansible
     * @return a value if it exists
     */
    private static String findTag(List<String> nameTags, Map<String, Object> tags) {
        String found = null;
        for (String nameTag : nameTags) {
            if (tags.containsKey(nameTag)) {
                Object value = tags.get(nameTag);
                found = valueString(value);
                break;
            }
        }
        return found;
    }

    /**
     * Casts an object to String
     * @param obj object to convert
     * @return a String object
     */
    private static String valueString(Object obj) {
        return getType(obj);
    }

    /**
     * Enum to manage Ansible tags
     */
    public enum NodeTag {

        HOSTNAME {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) throws ResourceModelSourceException{
                final List<String> hostnames = List.of("hostname", "ansible_host", "ansible_ssh_host");
                String nameTag = InventoryList.findTag(hostnames, tags);
                node.setHostname(Optional.ofNullable(nameTag)
                        .orElseThrow(() -> new ResourceModelSourceException(format(ERROR_MISSING_TAG, hostnames))));
            }
        },
        USERNAME {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) {
                final List<String> usernames = List.of("username", "ansible_user", "ansible_ssh_user", "ansible_user_id");
                String nameTag = InventoryList.findTag(usernames, tags);
                Optional.ofNullable(nameTag).ifPresent(node::setUsername);
            }
        },
        OS_FAMILY {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) {
                final List<String> osNames = List.of("osFamily", "ansible_os_family");
                String nameTag = InventoryList.findTag(osNames, tags);
                Optional.ofNullable(nameTag).ifPresent(node::setOsFamily);
            }
        },
        OS_NAME {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) {
                final List<String> familyNames = List.of("osName", "ansible_os_name");
                String nameTag = InventoryList.findTag(familyNames, tags);
                Optional.ofNullable(nameTag).ifPresent(node::setOsName);
            }
        },
        OS_ARCHITECTURE {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) {
                final List<String> architectureNames = List.of("osArch", "ansible_architecture");
                String nameTag = InventoryList.findTag(architectureNames, tags);
                Optional.ofNullable(nameTag).ifPresent(node::setOsArch);
            }
        },
        OS_VERSION {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) {
                final List<String> versionNames = List.of("osVersion", "ansible_kernel");
                String nameTag = InventoryList.findTag(versionNames, tags);
                Optional.ofNullable(nameTag).ifPresent(node::setOsVersion);
            }
        },
        DESCRIPTION {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) {
                Map<String, Object> lsbMap = InventoryList.getValue(tags, "ansible_lsb");
                if (lsbMap != null) {
                    String desc = InventoryList.valueString(lsbMap.get("description"));
                    Optional.ofNullable(desc).ifPresent(node::setDescription);
                }
                else {
                    Optional.ofNullable(InventoryList.getValue(tags, "ansible_distribution"))
                            .ifPresent(x -> node.setDescription(x + " "));
                    Optional.ofNullable(InventoryList.getValue(tags, "ansible_distribution_version"))
                            .ifPresent(x -> node.setDescription(x + " "));
                }
            }
        };

        /**
         * Processes an Ansible tag to build a Rundeck tag
         * @param node Rundeck node
         * @param tags Ansible tags
         * @throws ResourceModelSourceException
         */
        public abstract void handle(NodeEntryImpl node, Map<String, Object> tags) throws ResourceModelSourceException;
    }
}
