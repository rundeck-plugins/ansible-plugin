package com.rundeck.plugins.ansible.ansible;

import com.rundeck.plugins.ansible.util.AnsibleUtil;
import com.rundeck.plugins.ansible.util.ProcessExecutor;
import com.rundeck.plugins.ansible.util.VaultPrompt;
import lombok.Builder;
import lombok.Data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
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

    private AnsibleVault ansibleVault;
    private VaultPrompt vaultPrompt;
    private List<String> limits;

    private File tempInternalVaultFile;
    private File tempVaultFile;
    private File vaultPromptFile;
    private File tempLimitFile;

    public static final String ANSIBLE_INVENTORY = "ansible-inventory";

    /**
     * Executes Ansible command to bring all nodes from inventory
     * @return output in yaml format
     */
    public String getNodeList() throws IOException, AnsibleException {

        List<String> procArgs = new ArrayList<>();
        procArgs.add(ANSIBLE_INVENTORY);
        //inventory can be defined in ansible.cfg
        if(inventory!=null && !inventory.isEmpty()){
            procArgs.add("--inventory-file" + "=" + inventory);
        }
        procArgs.add("--list");
        procArgs.add("-y");

        Map<String, String> processEnvironment = new HashMap<>();
        if (configFile != null && !configFile.isEmpty()) {
            if (debug) {
                System.out.println(" ANSIBLE_CONFIG: " + configFile);
            }
            processEnvironment.put("ANSIBLE_CONFIG", configFile);
        }
        //set STDIN variables
        List<VaultPrompt> stdinVariables = new ArrayList<>();

        processAnsibleVault(stdinVariables, procArgs);
        processLimit(procArgs);

        procArgs.add("2>/dev/null");

        String allCmd = String.join(" ", procArgs);

        procArgs.clear();
        procArgs.add("bash");
        procArgs.add("-c");
        procArgs.add(allCmd);

        if(debug){
            System.out.println("getNodeList " + procArgs);
        }

        Process proc = null;

        try {
            proc = ProcessExecutor.builder().procArgs(procArgs)
                    .redirectErrorStream(true)
                    .environmentVariables(processEnvironment)
                    .stdinVariables(stdinVariables)
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

        } catch (IOException e) {
            throw new AnsibleException("ERROR: Ansible IO failure: " + e.getMessage(), e, AnsibleException.AnsibleFailureReason.IOFailure);
        } catch (InterruptedException e) {
            if (proc != null) {
                proc.destroy();
            }
            Thread.currentThread().interrupt();
            throw new AnsibleException("ERROR: Ansible Execution Interrupted: " + e.getMessage(), e, AnsibleException.AnsibleFailureReason.Interrupted);
        } catch (Exception e) {
            if (proc != null) {
                proc.destroy();
            }
            throw new AnsibleException("ERROR: Ansible execution returned with non zero code: " + e.getMessage(), e, AnsibleException.AnsibleFailureReason.Unknown);
        } finally {
            if (proc != null) {
                proc.getErrorStream().close();
                proc.getInputStream().close();
                proc.getOutputStream().close();
                proc.destroy();
            }
        }
    }

    private void processAnsibleVault(List<VaultPrompt> stdinVariables, List<String> procArgs)
            throws IOException {

        if (vaultPrompt == null) { return; }

        if(ansibleVault == null){
            tempInternalVaultFile = AnsibleVault.createVaultScriptAuth("ansible-script-vault");
            ansibleVault = AnsibleVault.builder()
                    .masterPassword(vaultPrompt.getVaultPassword())
                    .vaultPasswordScriptFile(tempInternalVaultFile)
                    .debug(debug).build();
        }

        stdinVariables.add(vaultPrompt);
        tempVaultFile = ansibleVault.getVaultPasswordScriptFile();
        procArgs.add("--vault-id");
        procArgs.add(tempVaultFile.getAbsolutePath());
    }

    private void processLimit(List<String> procArgs) throws IOException {

        if (limits == null) { return; }

        if (limits.size() == 1) {
            procArgs.add("-l");
            procArgs.add(limits.get(0));

        } else if (limits.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String limit : limits) {
                sb.append(limit).append("\n");
            }
            tempLimitFile = AnsibleUtil.createTemporaryFile("targets", sb.toString());

            procArgs.add("-l");
            procArgs.add("@" + tempLimitFile.getAbsolutePath());
        }
    }
}
