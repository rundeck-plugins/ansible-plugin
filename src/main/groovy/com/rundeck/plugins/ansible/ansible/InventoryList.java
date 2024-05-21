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

    @SuppressWarnings("unchecked")
    public static <T> T getValue(Map<String, Object> tag, final String field) {
        Object obj = null;
        if (Optional.ofNullable(tag.get(field)).isPresent()) {
            obj = tag.get(field);
        }
        return (T) obj;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getMap(Object obj) {
        return (T) obj;
    }

    public static void tagHandle(NodeTag nodetag, NodeEntryImpl node, Map<String, Object> tags)
            throws ResourceModelSourceException {
        nodetag.handle(node, tags);
    }

    private static String findTag(List<String> nameTags, Map<String, Object> tags) {
        String found = null;
        for (String nameTag : nameTags) {
            if (tags.containsKey(nameTag)) {
                Object value = tags.get(nameTag);
                if (value instanceof String) {
                    found = (String) value;
                }
                break;
            }
        }
        return found;
    }

    private static String valueString(Object obj) {
        if (obj instanceof String) {
            return (String) obj;
        }
        return null;
    }

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
            public void handle(NodeEntryImpl node, Map<String, Object> tags) throws ResourceModelSourceException {
                final List<String> usernames = List.of("username", "ansible_user", "ansible_ssh_user", "ansible_user_id");
                String nameTag = InventoryList.findTag(usernames, tags);
                node.setUsername(Optional.ofNullable(nameTag)
                        .orElseThrow(() -> new ResourceModelSourceException(format(ERROR_MISSING_TAG, usernames))));
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
                final List<String> versionNames = List.of("osVersion");
                String nameTag = InventoryList.findTag(versionNames, tags);
                Optional.ofNullable(nameTag).ifPresent(node::setOsArch);
            }
        },
        DESCRIPTION {
            @Override
            public void handle(NodeEntryImpl node, Map<String, Object> tags) throws ResourceModelSourceException {
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

        public abstract void handle(NodeEntryImpl node, Map<String, Object> tags) throws ResourceModelSourceException;
    }
}
