package com.rundeck.plugins.ansible.ansible;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

public class AnsibleInventory {

  public class AnsibleInventoryHosts {

    protected Map<String, Map<String, JsonElement>> hosts = new HashMap<String, Map<String, JsonElement>>();
    protected Map<String, AnsibleInventoryHosts> children = new HashMap<String, AnsibleInventoryHosts>();

    public AnsibleInventoryHosts addHost(String nodeName) {
      hosts.put(nodeName, new HashMap<String, JsonElement>());
      return this;
    }

    public AnsibleInventoryHosts addHost(String nodeName, String host, Map<String, String> attributes) {
      attributes.put("ansible_host", host);
      Map<String, JsonElement> attributesJson = new HashMap<String, JsonElement>();

      // Add ALL node attributes as inventory host variables
      for (Entry<String, String> attribute : attributes.entrySet() ) {
        JsonElement json;
        try {
          json = JsonParser.parseString(attribute.getValue());
        } catch (Exception e) {
          // convert Strings to Primitives
          json = new JsonPrimitive(attribute.getValue());
        }

        attributesJson.put(attribute.getKey(), json);
      }

      hosts.put(nodeName, attributesJson);
      return this;
    }

    public AnsibleInventoryHosts getOrAddChildHostGroup(String groupName) {
      children.putIfAbsent(groupName, new AnsibleInventoryHosts());
      return children.get(groupName);
    }
  }

  protected AnsibleInventoryHosts all = new AnsibleInventoryHosts();

  public AnsibleInventory addHost(String nodeName, String host, Map<String, String> attributes) {
    // Remove attributes that are reserved in Ansible
    String[] reserved = { "hostvars", "group_names", "groups", "environment" };
    for (String r: reserved){
      attributes.remove(r);
    }

    // Create Ansible groups by attribute
    // Group by osFamily is needed for windows hosts setup
    String[] attributeGroups = { "osFamily", "tags" };
    for (String g: attributeGroups) {
      if (attributes.containsKey(g)) {
        String[] groupNames = attributes.get(g).toLowerCase().split(",");
        for (String groupName: groupNames) {
          // Sanitize group name: Ansible only allows letters, numbers, underscores, and hyphens
          // Replace invalid characters (like colons) with underscores
          String sanitizedGroupName = groupName.trim().replaceAll("[^a-z0-9_\\-]", "_");
          all.getOrAddChildHostGroup(sanitizedGroupName).addHost(nodeName);
        }
      }
    }

    //remove "tags" since it's reserved in Ansible and we've already used it for grouping
    attributes.remove("tags");

    all.addHost(nodeName, host, attributes);
    return this;
  }
}
