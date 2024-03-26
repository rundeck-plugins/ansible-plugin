package com.rundeck.plugins.ansible.util;

import lombok.Builder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.io.File;
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

        OutputStream stdin = proc.getOutputStream();
        OutputStreamWriter stdinw = new OutputStreamWriter(stdin);

        if (stdinVariables != null) {
            try {

                for (String stdinVariable : stdinVariables) {
                    stdinw.write(stdinVariable);
                }
                stdinw.flush();
            } catch (Exception e) {
                System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
            }
        }
        stdinw.close();
        stdin.close();

        return proc;
    }

}
