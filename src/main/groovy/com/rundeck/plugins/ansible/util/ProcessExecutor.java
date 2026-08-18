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

    // Terminator vault-client.py reads to know the password is complete, since the
    // password itself may legitimately contain newlines and can no longer be used
    // as the delimiter.
    private static final int END_OF_TEXT = 3;

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
                    // Send password/content synchronously -- run() closes stdinw right
                    // after processPrompt() returns, so writing from a background
                    // thread here (as before) raced that close and could truncate
                    // the write.
                    stdinw.write(vaultPrompt.getVaultPassword());
                    stdinw.write(END_OF_TEXT);
                    stdinw.flush();
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
