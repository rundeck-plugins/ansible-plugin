package com.batix.rundeck.core;

import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;

import java.io.File;
import java.io.PrintWriter;

/**
 * Created by vincenzo.pirrone on 13/02/2017.
 */
public class AnsiblePlaybookBuilder {


    public static File withTasks(String tasks) throws ConfigurationException {
        try {
            File file = File.createTempFile("playbook", ".tmp.yml");
            file.deleteOnExit();
            PrintWriter writer = new PrintWriter(file);
            writer.write("- hosts: all\n");
            writer.write("  tasks:\n");
            for (String line : tasks.split("\n")) {
                writer.write(String.format("  %s\n", line));
            }
            writer.close();
            return file;
        } catch (Exception e) {
            throw new ConfigurationException("Could not write temporary playbook: " + e.getMessage());
        }
    }

    public static File withRole(String role, String args) throws ConfigurationException {
        try {
            File file = File.createTempFile("playbook", ".tmp.yml");
            file.deleteOnExit();
            PrintWriter writer = new PrintWriter(file);
            writer.write("- hosts: all\n");
            writer.write("  roles:\n");
            writer.write("  - { role: " + role);
            if (args != null && args.length() > 0) {
                writer.write(", " + args);
            }
            writer.write(" }\n");
            writer.close();
            return file;
        } catch (Exception e) {
            throw new ConfigurationException("Could not write temporary playbook: " + e.getMessage());
        }
    }

    private AnsiblePlaybookBuilder() {
    }

}
