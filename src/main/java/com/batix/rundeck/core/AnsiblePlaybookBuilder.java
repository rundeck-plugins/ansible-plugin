package com.batix.rundeck.core;

import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;

import java.io.File;
import java.io.PrintWriter;

/**
 * Created by vincenzo.pirrone on 13/02/2017.
 */
public class AnsiblePlaybookBuilder {

    private String tasks;

    public AnsiblePlaybookBuilder(String tasks) {
        this.tasks = tasks;
    }

    public File buildPlaybook() throws ConfigurationException {
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

}
