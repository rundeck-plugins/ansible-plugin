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

    private List<VaultPrompt> stdinVariables;

    private boolean redirectErrorStream;

    private File promptStdinLogFile;

    private boolean debug;


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
                for (VaultPrompt stdinVariable : stdinVariables) {
                    processPrompt(stdinw, stdinVariable);
                }
            } catch (Exception e) {
                System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
            }
        }

        stdinw.close();
        stdin.close();

        return proc;
    }

    private void processPrompt(OutputStreamWriter stdinw, final VaultPrompt vaultPrompt) throws Exception {
        if(promptStdinLogFile!=null){
            Thread stdinThread = new Thread(() -> {
                try {
                    stdinw.write(vaultPrompt.getVaultPassword());
                    stdinw.write(3); // end of text
                    stdinw.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            );

            //wait for prompt
            boolean promptFound = false;
            long start = System.currentTimeMillis();
            long end = start + 60 * 1000;
            BufferedReader reader = new BufferedReader(new FileReader(promptStdinLogFile));

            while (!promptFound && System.currentTimeMillis() < end){
                String currentLine = reader.readLine();
                if(debug){
                    System.out.println("waiting for vault password prompt ("+vaultPrompt.getVaultId()+")...");
                }
                if(currentLine!=null && currentLine.contains("Enter Password ("+vaultPrompt.getVaultId()+"):")){
                    if(debug) {
                        System.out.println(currentLine);
                    }
                    promptFound = true;
                    //send password / content
                    stdinThread.start();
                }
                Thread.sleep(2000);
            }
            reader.close();

        }else{
            stdinw.write(vaultPrompt.getVaultPassword());
            stdinw.flush();
        }
    }

}
