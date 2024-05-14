package com.rundeck.plugins.ansible.ansible;

import com.rundeck.plugins.ansible.util.ProcessExecutor;
import lombok.Builder;
import lombok.Data;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AnsibleInventoryList {

    private String inventory;
    private boolean debug;

    public static final String ANSIBLE_INVENTORY = "ansible-inventory";

    public String getNodeList() {

        List<String> procArgs = new ArrayList<>();

        procArgs.add(ANSIBLE_INVENTORY);
        procArgs.add("-i " + inventory);
        procArgs.add("--list");
        procArgs.add("-y");

        if(debug){
            System.out.println("getNodeList " + procArgs);
        }

        Process proc = null;

        try {
            proc = ProcessExecutor.builder().procArgs(procArgs)
                    .redirectErrorStream(true)
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
