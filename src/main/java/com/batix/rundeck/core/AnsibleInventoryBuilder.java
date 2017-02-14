package com.batix.rundeck.core;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

/**
 * Created by vincenzo.pirrone on 18/01/2017.
 */
public class AnsibleInventoryBuilder {

    private final Collection<INodeEntry> nodes;

    public AnsibleInventoryBuilder(Collection<INodeEntry> nodes) {
        this.nodes = nodes;
    }

    public File buildInventory() throws ConfigurationException {
        try {
            File file = File.createTempFile("inventory", ".tmp");
            file.deleteOnExit();
            PrintWriter writer = new PrintWriter(file);
            for (INodeEntry e : nodes) {
                writer.write(String.format("%s ansible_host=%s\n", e.getNodename(), e.getHostname()));
            }
            writer.close();
            return file;
        } catch (Exception e) {
            throw new ConfigurationException("Could not write temporary inventory: " + e.getMessage());
        }
    }


//    private Map<String, String> entries;
//
//    public static AnsibleInventoryBuilder buildFromNodeSet(INodeSet nodes) {
//        Map<String, String> entries = new HashMap<>();
//        for (INodeEntry node : nodes) {
//            addEntry(entries, node);
//        }
//        return new AnsibleInventoryBuilder(entries);
//    }
//
//    private static void addEntry(Map<String, String> entries, INodeEntry node) {
//        try {
//            InetAddress address = InetAddress.getByName(node.extractHostname());
//            entries.put(node.getNodename(), address.getHostAddress());
//        } catch (UnknownHostException e) {
//            Listener listener = ListenerFactory.getListener(System.out);
//            listener.output(String.format("[WARNING] Cannot add entry for host %s: %s", node.getNodename(), e.getMessage()));
//        }
//    }
//
//    private AnsibleInventoryBuilder(Map<String, String> entries) {
//        this.entries = entries;
//    }
//
//    public String writeToTempFile() throws IOException {
//        File tempFile = File.createTempFile("inventory", ".tmp");
//        PrintWriter writer = new PrintWriter(tempFile);
//        for (Map.Entry<String, String> e : entries.entrySet()) {
//            writer.write(String.format("%s ansible_host=%s\n", e.getKey(), e.getValue()));
//        }
//        writer.close();
//        return tempFile.getAbsolutePath();
//    }
//
//    @Override
//    public String toString() {
//        StringBuilder builder = new StringBuilder();
//        for (String host: entries.keySet()) {
//            builder.append(host).append(",");
//        }
//        return builder.toString();
//    }
}
