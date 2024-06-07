package com.rundeck.plugins.ansible.util;

import lombok.Builder;

import java.io.*;
import java.util.List;
import java.util.Map;

@Builder
public class ProcessExecutor {

    private List<String> procArgs;

    private File baseDirectory;

    private Map<String, String> environmentVariables;

    private List<String> stdinVariables;

    private boolean redirectErrorStream;


    public Process run() throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(procArgs)
                .redirectErrorStream(redirectErrorStream);

        if(baseDirectory!=null){
            processBuilder.directory(baseDirectory);
        }

        if(environmentVariables!=null){
            Map<String, String> processEnvironment = processBuilder.environment();

            for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                processEnvironment.put(entry.getKey(), entry.getValue());
            }
        }

        Process proc = processBuilder.start();

        if (stdinVariables != null && !stdinVariables.isEmpty()){
            OutputStream stdin = proc.getOutputStream();
            OutputStreamWriter stdinw = new OutputStreamWriter(stdin);
            BufferedWriter writer = new BufferedWriter(stdinw);
            try {

                for (String stdinVariable : stdinVariables) {
                    writer.write(stdinVariable);
                    writer.newLine();
                    writer.flush();
                }

            } catch (Exception e) {
                System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
            }
            writer.close();
            stdinw.close();
            stdin.close();
        }


        return proc;
    }

}
