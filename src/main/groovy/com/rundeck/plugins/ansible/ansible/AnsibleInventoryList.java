package com.rundeck.plugins.ansible.ansible;

import com.rundeck.plugins.ansible.util.ProcessExecutor;
import lombok.Builder;
import lombok.Data;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnsibleInventoryList {

    private String inventory;
    private String configFile;
    private boolean debug;

    public static final String ANSIBLE_INVENTORY = "ansible-inventory";

    /**
     * Executes Ansible command to bring all nodes from inventory
     * @return output in yaml format
     */
    public String getNodeList() {

        List<String> procArgs = new ArrayList<>();
        procArgs.add(ANSIBLE_INVENTORY);
        procArgs.add("--inventory-file" + "=" + inventory);
        procArgs.add("--list");
        procArgs.add("-y");

        Map<String, String> processEnvironment = new HashMap<>();
        if (configFile != null && !configFile.isEmpty()) {
            if (debug) {
                System.out.println(" ANSIBLE_CONFIG: " + configFile);
            }
            processEnvironment.put("ANSIBLE_CONFIG", configFile);
        }

        if(debug){
            System.out.println("getNodeList " + procArgs);
        }

        Process proc = null;

        try {
            proc = ProcessExecutor.builder().procArgs(procArgs)
                    .redirectErrorStream(true)
                    .environmentVariables(processEnvironment)
                    .build().run();

            StringBuilder stringBuilder = new StringBuilder();

            final InputStream stdoutInputStream = proc.getInputStream();
            final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdoutInputStream));

            String line1;
            while ((line1 = stdoutReader.readLine()) != null) {
                stringBuilder.append(line1).append("\n");
            }

            int exitCode = proc.waitFor();

            if (exitCode != 0) {
                System.err.println("ERROR: getNodeList: " + procArgs);
                return null;
            }
            return stringBuilder.toString();

        } catch (Exception e) {
            System.err.println("error getNodeList: " + e.getMessage());
            return null;
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }
    }
}
