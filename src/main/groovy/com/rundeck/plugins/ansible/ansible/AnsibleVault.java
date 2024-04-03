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

@Data
@Builder
public class AnsibleVault {

    private File vaultPasswordScriptFile;
    private String masterPassword;
    private boolean debug;
    private Path baseDirectory;
    private Path ansibleBinariesDirectory;

    public final String ANSIBLE_VAULT_COMMAND = "ansible-vault";

    public boolean checkAnsibleVault() {
        List<String> procArgs = new ArrayList<>();
        String ansibleCommand = ANSIBLE_VAULT_COMMAND;
        if (ansibleBinariesDirectory != null) {
            ansibleCommand = Paths.get(ansibleBinariesDirectory.toFile().getAbsolutePath(), ansibleCommand).toFile().getAbsolutePath();
        }
        procArgs.add(ansibleCommand);
        procArgs.add("--version");

        Process proc = null;

        try {
            proc = ProcessExecutor.builder().procArgs(procArgs)
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
        procArgs.add(ansibleCommand);
        procArgs.add("encrypt_string");
        procArgs.add("--vault-id");
        procArgs.add("internal-encrypt@" + vaultPasswordScriptFile.getAbsolutePath());

        if(debug){
            System.out.println("encryptVariable " + key + ": " + procArgs);
        }

        //send values to STDIN in order
        List<String> stdinVariables = new ArrayList<>();

        Process proc = null;

        try {
            proc = ProcessExecutor.builder().procArgs(procArgs)
                    .baseDirectory(baseDirectory.toFile())
                    .stdinVariables(stdinVariables)
                    .redirectErrorStream(true)
                    .build().run();

            OutputStream stdin = proc.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));

            //send master password
            Thread.sleep(1500);
            writer.write(masterPassword);
            writer.newLine();
            writer.flush();

            //send content to encrypt
            Thread.sleep(1500);
            writer.write(content);
            writer.flush();

            writer.close();
            stdin.close();

            StringBuilder stringBuilder = new StringBuilder();

            final InputStream stdoutInputStream = proc.getInputStream();
            final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdoutInputStream));

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

            int exitCode = proc.waitFor();

            if (exitCode != 0) {
                System.err.println("ERROR: encryptFileAnsibleVault:" + procArgs);
                return null;
            }
            return stringBuilder.toString();

        } catch (Exception e) {
            System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
            return null;
        } finally {
            // Make sure to always cleanup on failure and success
            if (proc != null) {
                proc.destroy();
            }
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
