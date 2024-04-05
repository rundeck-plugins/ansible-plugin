package com.rundeck.plugins.ansible.ansible;

import com.rundeck.plugins.ansible.util.AnsibleUtil;
import com.rundeck.plugins.ansible.util.ProcessExecutor;
import lombok.Builder;
import lombok.Data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;

@Data
@Builder
public class AnsibleVault {

    private File vaultPasswordScriptFile;
    private String masterPassword;
    private boolean debug;
    private Path baseDirectory;
    private Path ansibleBinariesDirectory;

    public final String ANSIBLE_VAULT_COMMAND = "ansible-vault";

    private ProcessExecutor.ProcessExecutorBuilder processExecutorBuilder;

    public boolean checkAnsibleVault() {
        List<String> procArgs = new ArrayList<>();
        String ansibleCommand = ANSIBLE_VAULT_COMMAND;
        if (ansibleBinariesDirectory != null) {
            ansibleCommand = Paths.get(ansibleBinariesDirectory.toFile().getAbsolutePath(), ansibleCommand).toFile().getAbsolutePath();
        }

        if(processExecutorBuilder==null){
            processExecutorBuilder = ProcessExecutor.builder();
        }

        procArgs.add(ansibleCommand);
        procArgs.add("--version");

        Process proc = null;

        try {
            proc = processExecutorBuilder.procArgs(procArgs)
                    .redirectErrorStream(true)
                    .build().run();

            int exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to check ansible-vault: " + e.getMessage());
            return false;
        }finally {
            if (proc != null) {
                proc.destroy();
            }
        }
    }

    public String encryptVariable(String key,
                                  String content ) throws IOException {

        List<String> procArgs = new ArrayList<>();
        String ansibleCommand = ANSIBLE_VAULT_COMMAND;
        if (ansibleBinariesDirectory != null) {
            ansibleCommand = Paths.get(ansibleBinariesDirectory.toFile().getAbsolutePath(), ansibleCommand).toFile().getAbsolutePath();
        }

        if(processExecutorBuilder==null){
            processExecutorBuilder = ProcessExecutor.builder();
        }

        procArgs.add(ansibleCommand);
        procArgs.add("encrypt_string");
        procArgs.add("--vault-id");
        procArgs.add("internal-encrypt@" + vaultPasswordScriptFile.getAbsolutePath());

        if(debug){
            System.out.println("encryptVariable " + key + ": " + procArgs);
        }

        File promptFile = File.createTempFile("vault-prompt", ".log");

        Map<String, String> env = new HashMap<>();
        env.put("LOG_PATH", promptFile.getAbsolutePath());

        Process proc = null;

        try {
            proc = processExecutorBuilder.procArgs(procArgs)
                    .baseDirectory(baseDirectory.toFile())
                    .environmentVariables(env)
                    .redirectErrorStream(true)
                    .build().run();

            final InputStream proccesInputStream = proc.getInputStream();
            final OutputStream processOutputStream = proc.getOutputStream();

            //capture output thread
            Callable<String> readOutputTask = () -> {
                return readOutput(proccesInputStream);
            };

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(readOutputTask);

            Thread stdinThread = new Thread(() -> sendValuesStdin(processOutputStream, masterPassword, content));

            long start = System.currentTimeMillis();
            long end = start + 60 * 1000;

            //wait for prompt
            boolean promptFound = false;
            while (!promptFound && System.currentTimeMillis() < end) {
                BufferedReader reader = new BufferedReader(new FileReader(promptFile));
                String currentLine = reader.readLine();
                if(currentLine!=null && currentLine.contains("Enter Password:")){
                    promptFound = true;
                    //send password / content
                    stdinThread.start();
                    reader.close();
                }else{
                    Thread.sleep(1500);
                }
            }

            if(!promptFound){
                throw new RuntimeException("Failed to find prompt for ansible-vault");
            }

            int exitCode = proc.waitFor();

            //get encrypted value
            String result = future.get();
            executor.shutdown();

            if (exitCode != 0) {
                throw new RuntimeException("ERROR: encryptFileAnsibleVault:" + procArgs);
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt variable: " + e.getMessage());
        } finally {
            // Make sure to always cleanup on failure and success
            if (proc != null) {
                proc.destroy();
            }

            if(promptFile!=null && promptFile.delete()){
                promptFile.deleteOnExit();
            }
        }
    }

    String readOutput(InputStream proccesInputStream) {
        try (
                InputStreamReader isr = new InputStreamReader(proccesInputStream);
                BufferedReader stdoutReader = new BufferedReader(isr);
        ) {

            StringBuilder stringBuilder = new StringBuilder();
            String line1 = null;
            boolean capture = false;
            while ((line1 = stdoutReader.readLine()) != null) {
                if (line1.toLowerCase().contains("!vault")) {
                    capture = true;
                }
                if (capture) {
                    stringBuilder.append(line1).append("\n");
                }
            }
            return stringBuilder.toString();
        } catch (Throwable e) {
            throw new RuntimeException("error reading output from ansible-vault", e);
        }
    }

    void sendValuesStdin(OutputStream stdin, String masterPassword, String content){
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin))) {
            //send master password
            writer.write(masterPassword);
            writer.newLine();
            writer.flush();

            //send content to encrypt
            Thread.sleep(1500);
            writer.write(content);
            writer.flush();

            writer.close();
            stdin.close();

        } catch (Throwable e) {
            throw new RuntimeException("error sending stdin for ansible-vault", e);
        }
    }

    public static File createVaultScriptAuth(String suffix) throws IOException {
        File tempInternalVaultFile = File.createTempFile("ansible-runner", suffix + "-client.py");

        try {
            Files.copy(AnsibleUtil.class.getClassLoader().getResourceAsStream("vault-client.py"),
                    tempInternalVaultFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IOException("Failed to copy vault-client.py", e);
        }

        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(tempInternalVaultFile.toPath(), perms);

        return tempInternalVaultFile;
    }

}
