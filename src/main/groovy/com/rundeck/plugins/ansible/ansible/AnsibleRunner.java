package com.rundeck.plugins.ansible.ansible;

import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rundeck.plugins.ansible.util.*;
import com.dtolabs.rundeck.core.utils.SSHAgentProcess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Builder;
import lombok.Data;


import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Builder
@Data
public class AnsibleRunner {

    enum AnsibleCommand {
        AdHoc("ansible"),
        PlaybookPath("ansible-playbook"),
        PlaybookInline("ansible-playbook");

        final String command;

        AnsibleCommand(String command) {
            this.command = command;
        }
    }

    public static class AnsibleRunnerBuilder {
        public AnsibleRunnerBuilder limits(String host) {
            List<String> hosts = new ArrayList<>();
            hosts.add(host);
            this.limits = hosts;
            return this;
        }

        public AnsibleRunnerBuilder limits(List<String> hosts) {
            this.limits = hosts;
            return this;
        }

        /**
         * Specify in which directory Ansible is run, noting it is a temporary directory.
         */
        public AnsibleRunnerBuilder tempDirectory(Path dir) {
            if (dir != null) {
                this.baseDirectory = dir;
                this.usingTempDirectory = true;
            }
            return this;
        }
    }

    public static AnsibleRunner.AnsibleRunnerBuilder adHoc(String module, String arg) {
        AnsibleRunner.AnsibleRunnerBuilder builder = AnsibleRunner.builder();
        builder.type(AnsibleCommand.AdHoc);
        builder.module(module);
        builder.arg(arg);
        return builder;
    }

    public static AnsibleRunner.AnsibleRunnerBuilder playbookPath(String playbook) {
        AnsibleRunner.AnsibleRunnerBuilder builder = AnsibleRunner.builder();
        builder.type(AnsibleCommand.PlaybookPath);
        builder.playbook(playbook);
        return builder;
    }

    public static AnsibleRunner.AnsibleRunnerBuilder playbookInline(String playbook) {
        AnsibleRunner.AnsibleRunnerBuilder builder = AnsibleRunner.builder();
        builder.type(AnsibleCommand.PlaybookInline);
        builder.playbook(playbook);
        return builder;
    }

    public static AnsibleRunner buildAnsibleRunner(AnsibleRunnerContextBuilder contextBuilder) throws ConfigurationException {

        AnsibleRunner.AnsibleRunnerBuilder ansibleRunnerBuilder = null;

        String playbook;
        String module;

        if ((playbook = contextBuilder.getPlaybookPath()) != null) {
            ansibleRunnerBuilder = AnsibleRunner.playbookPath(playbook);
        } else if ((playbook = contextBuilder.getPlaybookInline()) != null) {
            ansibleRunnerBuilder = AnsibleRunner.playbookInline(playbook);
        } else if ((module = contextBuilder.getModule()) != null) {
            ansibleRunnerBuilder = AnsibleRunner.adHoc(module, contextBuilder.getModuleArgs());
        } else {
            throw new ConfigurationException("Missing module or playbook job arguments");
        }

        final AnsibleDescribable.AuthenticationType authType = contextBuilder.getSshAuthenticationType();
        if (AnsibleDescribable.AuthenticationType.privateKey == authType) {
            final String privateKey = contextBuilder.getSshPrivateKey();
            if (privateKey != null) {
                ansibleRunnerBuilder.sshPrivateKey(privateKey);
            }

            if (contextBuilder.getUseSshAgent()) {
                ansibleRunnerBuilder.sshUseAgent(true);

                String passphraseOption = contextBuilder.getPassphrase();
                ansibleRunnerBuilder.sshPassphrase(passphraseOption);
            }
        } else if (AnsibleDescribable.AuthenticationType.password == authType) {
            final String password = contextBuilder.getSshPassword();
            if (password != null) {
                ansibleRunnerBuilder.sshUsePassword(Boolean.TRUE).sshPass(password);
            }
        }

        // set rundeck options as environment variables
//        Map<String,String> options = context.getDataContext().get("option");
//        if (options != null) {
//            runner = runner.options(options);
//        }

        String inventory = contextBuilder.getInventory();
        if (inventory != null) {
            ansibleRunnerBuilder.inventory(inventory);
        }

        String limit = contextBuilder.getLimit();
        if (limit != null) {
            ansibleRunnerBuilder.limits(limit);
        }

        Boolean debug = contextBuilder.getDebug();
        if (debug != null) {
            if (debug == Boolean.TRUE) {
                ansibleRunnerBuilder.debug(Boolean.TRUE);
            } else {
                ansibleRunnerBuilder.debug(Boolean.FALSE);
            }
        }

        String extraParams = contextBuilder.getExtraParams();
        if (extraParams != null) {
            ansibleRunnerBuilder.extraParams(extraParams);
        }

        String extraVars = contextBuilder.getExtraVars();
        if (extraVars != null) {
            ansibleRunnerBuilder.extraVars(extraVars);
        }

        String user = contextBuilder.getSshUser();
        if (user != null) {
            ansibleRunnerBuilder.sshUser(user);
        }

        String vault = contextBuilder.getVaultKey();
        if (vault != null) {
            ansibleRunnerBuilder.vaultPass(vault);
        }

        Integer timeout = contextBuilder.getSSHTimeout();
        if (timeout != null) {
            ansibleRunnerBuilder.sshTimeout(timeout);
        }

        Boolean become = contextBuilder.getBecome();
        if (become != null) {
            ansibleRunnerBuilder.become(become);
        }

        String become_user = contextBuilder.getBecomeUser();
        if (become_user != null) {
            ansibleRunnerBuilder.becomeUser(become_user);
        }

        AnsibleDescribable.BecomeMethodType become_method = contextBuilder.getBecomeMethod();
        if (become_method != null) {
            ansibleRunnerBuilder.becomeMethod(become_method.name());
        }

        String become_password = contextBuilder.getBecomePassword();
        if (become_password != null) {
            ansibleRunnerBuilder.becomePassword(become_password);
        }

        String executable = contextBuilder.getExecutable();
        if (executable != null) {
            ansibleRunnerBuilder.executable(executable);
        }

        String configFile = contextBuilder.getConfigFile();
        if (configFile != null) {
            ansibleRunnerBuilder.configFile(configFile);
        }

        String baseDir = contextBuilder.getBaseDir();
        if (baseDir != null) {
            ansibleRunnerBuilder.baseDirectory(java.nio.file.Path.of(baseDir));
        }

        String binariesFilePath = contextBuilder.getBinariesFilePath();
        if (binariesFilePath != null) {
            ansibleRunnerBuilder.ansibleBinariesDirectory(java.nio.file.Path.of(binariesFilePath));
        }

        boolean encryptExtraVars = contextBuilder.encryptExtraVars();
        if (encryptExtraVars) {
            ansibleRunnerBuilder.encryptExtraVars(true);
        }

        return ansibleRunnerBuilder.build();
    }


    @Builder.Default
    private boolean done = false;

    private final AnsibleCommand type;

    private String playbook;
    private String inventory;
    private String module;
    private String arg;
    private String extraVars;
    private String extraParams;
    private String vaultPass;
    @Builder.Default
    private boolean ignoreErrors = false;

    // ansible ssh args
    @Builder.Default
    private boolean sshUsePassword = false;
    private String sshPass;
    private String sshUser;
    private String sshPrivateKey;
    private Integer sshTimeout;
    @Builder.Default
    private boolean sshUseAgent = false;
    private String sshPassphrase;
    private SSHAgentProcess sshAgent;
    @Builder.Default
    private Integer sshAgentTimeToLive = 0;

    // ansible become args
    @Builder.Default
    protected Boolean become = Boolean.FALSE;
    protected String becomeMethod;
    protected String becomeUser;
    protected String becomePassword;

    @Builder.Default
    private boolean debug = false;

    private Path baseDirectory;
    private Path ansibleBinariesDirectory;
    private boolean usingTempDirectory;
    private boolean retainTempDirectory;
    private List<String> limits;
    private int result;
    @Builder.Default
    private Map<String, String> options = new HashMap<>();
    @Builder.Default
    private String executable = "sh";

    protected String configFile;

    private Listener listener;

    private String generatedVaultPassword;

    @Builder.Default
    private boolean encryptExtraVars = false;

    static ObjectMapper mapperYaml = new ObjectMapper(new YAMLFactory());
    static ObjectMapper mapperJson = new ObjectMapper();


    /**
     * Add options passed as Environment variables to ansible
     */
//  public AnsibleRunner options(Map<String, String> options) {
//    this.options.putAll(options);
//    return this;
//  }

    public void deleteTempDirectory(Path tempDirectory) throws IOException {
        Files.walkFileTree(tempDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    /**
     * Splits up a command and its arguments inf form of a string into a list of strings.
     *
     * @param commandline String with a possibly complex command and arguments
     * @return a list of arguments
     */
    public static List<String> tokenizeCommand(String commandline) {
        List<String> tokens = ArgumentTokenizer.tokenize(commandline, true);
        List<String> args = new ArrayList<>();
        for (String token : tokens) {
            args.add(token.replaceAll("\\\\", "\\\\").replaceAll("^\"|\"$", ""));
        }
        return args;
    }

    public int run() throws Exception {
        if (done) {
            throw new IllegalStateException("already done");
        }
        done = true;

        if (baseDirectory == null) {
            // Use a temporary directory and mark it for possible removal later
            this.usingTempDirectory = true;
            baseDirectory = Files.createTempDirectory("ansible-rundeck");
        }

        File tempPlaybook = null;
        File tempFile = null;
        File tempPkFile = null;
        File tempVarsFile = null;
        File tempInternalVaultFile = null;
        File tempVaultFile = null;

        List<String> procArgs = new ArrayList<>();

        String ansibleCommand = type.command;
        if (ansibleBinariesDirectory != null) {
            ansibleCommand = Paths.get(ansibleBinariesDirectory.toFile().getAbsolutePath(), ansibleCommand).toFile().getAbsolutePath();
        }
        procArgs.add(ansibleCommand);

        // parse arguments
        if (type == AnsibleCommand.AdHoc) {
            procArgs.add("all");

            procArgs.add("-m");
            procArgs.add(module);

            if (arg != null && !arg.isEmpty()) {
                procArgs.add("-a");
                procArgs.add(arg);
            }
            procArgs.add("-t");
            procArgs.add(baseDirectory.toFile().getAbsolutePath());
        } else if (type == AnsibleCommand.PlaybookPath) {
            procArgs.add(playbook);
        } else if (type == AnsibleCommand.PlaybookInline) {
            tempPlaybook = AnsibleUtil.createTemporaryFile("playbook", playbook);
            procArgs.add(tempPlaybook.getAbsolutePath());
        }

        if (encryptExtraVars) {
            UUID uuid = UUID.randomUUID();
            generatedVaultPassword = uuid.toString();
            tempInternalVaultFile = AnsibleUtil.createTemporaryFile("interal-vault", generatedVaultPassword);
            procArgs.add("--vault-id");
            procArgs.add("interval-encypt@" + tempInternalVaultFile.getAbsolutePath());
        }

        if (inventory != null && !inventory.isEmpty()) {
            procArgs.add("--inventory-file" + "=" + inventory);
        }

        if (limits != null && limits.size() == 1) {
            procArgs.add("-l");
            procArgs.add(limits.get(0));

        } else if (limits != null && limits.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String limit : limits) {
                sb.append(limit).append("\n");
            }
            tempFile = AnsibleUtil.createTemporaryFile("targets", sb.toString());

            procArgs.add("-l");
            procArgs.add("@" + tempFile.getAbsolutePath());
        }

        if (debug == Boolean.TRUE) {
            procArgs.add("-vvv");
        }

        if (extraVars != null && !extraVars.isEmpty()) {
            String addeExtraVars = extraVars;

            if (encryptExtraVars) {
                addeExtraVars = encryptExtraVarsKey(extraVars, tempInternalVaultFile);
            }

            tempVarsFile = AnsibleUtil.createTemporaryFile("extra-vars", addeExtraVars);
            procArgs.add("--extra-vars" + "=" + "@" + tempVarsFile.getAbsolutePath());
        }

        if (vaultPass != null && !vaultPass.isEmpty()) {
            //procArgs.add("--vault-password-file" + "=" + tempVaultFile.getAbsolutePath());
            tempVaultFile = AnsibleUtil.createTemporaryFile("vault", vaultPass);
            procArgs.add("--vault-id");
            procArgs.add(tempVaultFile.getAbsolutePath());
        }

        if (sshPrivateKey != null && !sshPrivateKey.isEmpty()) {
            String privateKeyData = sshPrivateKey.replaceAll("\r\n", "\n");
            tempPkFile = AnsibleUtil.createTemporaryFile("id_rsa", privateKeyData);

            // Only the owner can read and write
            Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(tempPkFile.toPath(), perms);

            if (sshUseAgent) {
                registerKeySshAgent(tempPkFile.getAbsolutePath());
            }
            procArgs.add("--private-key" + "=" + tempPkFile.toPath());
        }

        if (sshUser != null && sshUser.length() > 0) {
            procArgs.add("--user" + "=" + sshUser);
        }

        if (sshUsePassword) {
            procArgs.add("--ask-pass");
        }

        if (sshTimeout != null && sshTimeout > 0) {
            procArgs.add("--timeout" + "=" + sshTimeout);
        }

        if (become) {
            procArgs.add("--become");
            if (becomePassword != null && !becomePassword.isEmpty()) {
                procArgs.add("--ask-become-pass");
            }
        }

        if (becomeMethod != null && !becomeMethod.isEmpty()) {
            procArgs.add("--become-method" + "=" + becomeMethod);
        }

        if (becomeUser != null && !becomeUser.isEmpty()) {
            procArgs.add("--become-user" + "=" + becomeUser);
        }

        // default the listener to stdout logger
        if (listener == null) {
            listener = ListenerFactory.getListener(System.out);
        }

        if (extraParams != null && !extraParams.isEmpty()) {
            procArgs.addAll(tokenizeCommand(extraParams));
        }

        if (debug) {
            System.out.println(" procArgs: " + procArgs);
        }

        ProcessExecutor.ProcessExecutorBuilder processExecutorBuilder = ProcessExecutor.builder()
                .procArgs(procArgs);

        if (baseDirectory != null) {
            processExecutorBuilder.baseDirectory(baseDirectory.toFile());
        }
        Process proc = null;

        //SET env variables
        Map<String, String> processEnvironment = new HashMap<>();

        if (configFile != null && !configFile.isEmpty()) {
            if (debug) {
                System.out.println(" ANSIBLE_CONFIG: " + configFile);
            }

            processEnvironment.put("ANSIBLE_CONFIG", configFile);
        }

//    for (String optionName : this.options.keySet()) {
//      processEnvironment.put(optionName, this.options.get(optionName));
//    }

        if (sshUseAgent && sshAgent != null) {
            processEnvironment.put("SSH_AUTH_SOCK", this.sshAgent.getSocketPath());
        }

        processExecutorBuilder.environmentVariables(processEnvironment);

        //set STDIN variables
        List<String> stdinVariables = new ArrayList<>();
        if (sshUsePassword) {
            if (sshPass != null && !sshPass.isEmpty()) {
                stdinVariables.add(sshPass + "\n");
            }
        }

        if (become) {
            if (becomePassword != null && !becomePassword.isEmpty()) {
                stdinVariables.add(becomePassword + "\n");
            }
        }

        if (encryptExtraVars) {
            stdinVariables.add(generatedVaultPassword + "\n");
        }

        if (vaultPass != null && !vaultPass.isEmpty()) {
            stdinVariables.add(vaultPass + "\n");
        }

        processExecutorBuilder.stdinVariables(stdinVariables);

        try {
            proc = processExecutorBuilder.build().run();

            Thread errthread = Logging.copyStreamThread(proc.getErrorStream(), listener);
            Thread outthread = Logging.copyStreamThread(proc.getInputStream(), listener);
            errthread.start();
            outthread.start();
            result = proc.waitFor();
            outthread.join();
            errthread.join();
            System.err.flush();
            System.out.flush();

            if (sshUseAgent) {
                if (sshAgent != null) {
                    sshAgent.stopAgent();
                }
            }

            if (result != 0) {
                if (!ignoreErrors) {
                    throw new AnsibleException("ERROR: Ansible execution returned with non zero code.",
                            AnsibleException.AnsibleFailureReason.AnsibleNonZero);
                }
            }
        } catch (InterruptedException e) {
            if (proc != null) {
                proc.destroy();
            }
            Thread.currentThread().interrupt();
            throw new AnsibleException("ERROR: Ansible Execution Interrupted.", e, AnsibleException.AnsibleFailureReason.Interrupted);
        } catch (IOException e) {
            throw new AnsibleException("ERROR: Ansible IO failure: " + e.getMessage(), e, AnsibleException.AnsibleFailureReason.IOFailure);
        } catch (AnsibleException e) {
            throw e;
        } catch (Exception e) {
            if (proc != null) {
                proc.destroy();
            }
            throw new AnsibleException("ERROR: Ansible execution returned with non zero code.", e, AnsibleException.AnsibleFailureReason.Unknown);
        } finally {
            // Make sure to always cleanup on failure and success
            if (proc != null) {
                proc.getErrorStream().close();
                proc.getInputStream().close();
                proc.getOutputStream().close();
                proc.destroy();
            }

            if (tempFile != null && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
            if (tempPkFile != null && !tempPkFile.delete()) {
                tempPkFile.deleteOnExit();
            }
            if (tempVarsFile != null && !tempVarsFile.delete()) {
                tempVarsFile.deleteOnExit();
            }
            if (tempPlaybook != null && !tempPlaybook.delete()) {
                tempPlaybook.deleteOnExit();
            }

            if (usingTempDirectory && !retainTempDirectory) {
                deleteTempDirectory(baseDirectory);
            }
        }

        return result;
    }

    public boolean registerKeySshAgent(String keyPath) throws Exception {

        if (sshAgent == null) {
            sshAgent = new SSHAgentProcess(this.sshAgentTimeToLive);
        }

        List<String> procArgs = new ArrayList<>();
        procArgs.add("/usr/bin/ssh-add");
        procArgs.add(keyPath);

        if (debug) {
            System.out.println("ssh-agent socket " + sshAgent.getClass());
            System.out.println(" registerKeySshAgent: " + procArgs);
        }

        Map<String, String> env = new HashMap<>();
        env.put("SSH_AUTH_SOCK", this.sshAgent.getSocketPath());

        File tempPassVarsFile = null;
        if (sshPassphrase != null && sshPassphrase.length() > 0) {
            tempPassVarsFile = File.createTempFile("ansible-runner", "ssh-add-check");
            tempPassVarsFile.setExecutable(true);

            List<String> passScript = new ArrayList<>();
            passScript.add("read SECRET");
            passScript.add("echo $SECRET");

            Files.write(tempPassVarsFile.toPath(),passScript);

            env.put("DISPLAY", "0");
            env.put("SSH_ASKPASS", tempPassVarsFile.getAbsolutePath());
        }

        List<String> stdinVariables = new ArrayList<>();
        if (sshPassphrase != null && !sshPassphrase.isEmpty()) {
            stdinVariables.add(sshPassphrase + "\n");
        }

        ProcessExecutor processExecutor = ProcessExecutor.builder()
                .procArgs(procArgs)
                .baseDirectory(baseDirectory.toFile())
                .environmentVariables(env)
                .stdinVariables(stdinVariables)
                .build();

        Process proc = null;

        try {
            proc = processExecutor.run();

            Thread errthread = Logging.copyStreamThread(proc.getErrorStream(), ListenerFactory.getListener(System.err));
            Thread outthread = Logging.copyStreamThread(proc.getInputStream(), ListenerFactory.getListener(System.out));
            errthread.start();
            outthread.start();

            int exitCode = proc.waitFor();

            outthread.join();
            errthread.join();
            System.err.flush();
            System.out.flush();

            if (exitCode != 0) {
                throw new AnsibleException("ERROR: ssh-add returns with non zero code:" + procArgs,
                        AnsibleException.AnsibleFailureReason.AnsibleNonZero);
            }

        } catch (IOException e) {
            throw new AnsibleException("ERROR: error adding private key to ssh-agent." + procArgs, e, AnsibleException.AnsibleFailureReason.Unknown);
        } catch (InterruptedException e) {
            if (proc != null) {
                proc.destroy();
            }
            Thread.currentThread().interrupt();
            throw new AnsibleException("ERROR: error adding private key to ssh-agen Interrupted.", e, AnsibleException.AnsibleFailureReason.Interrupted);
        } finally {
            // Make sure to always cleanup on failure and success
            if (proc != null) {
                proc.destroy();
            }
            if(tempPassVarsFile!=null && !tempPassVarsFile.delete()){
                tempPassVarsFile.deleteOnExit();
            }
        }

        return true;
    }

    protected String encryptVariable(String content, File vaultPassword) throws IOException {

        List<String> procArgs = new ArrayList<>();
        procArgs.add("ansible-vault");
        procArgs.add("encrypt_string");
        procArgs.add("--vault-id");
        procArgs.add("interval-encypt@" + vaultPassword.getAbsolutePath());

        //send values to STDIN in order
        List<String> stdinVariables = new ArrayList<>();
        stdinVariables.add(content);

        Process proc = null;

        try {
            proc = ProcessExecutor.builder().procArgs(procArgs)
                    .baseDirectory(baseDirectory.toFile())
                    .stdinVariables(stdinVariables)
                    .redirectErrorStream(true)
                    .build().run();

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


    public String encryptExtraVarsKey(String extraVars, File vaultPasswordFile) throws Exception {
        Map<String, String> extraVarsMap = null;
        Map<String, String> encryptedExtraVarsMap = new HashMap<>();
        try {
            extraVarsMap = mapperYaml.readValue(extraVars, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            try {
                extraVarsMap = mapperJson.readValue(extraVars, new TypeReference<Map<String, String>>() {
                });
            } catch (Exception e2) {
                System.err.println("error encryptExtraVars  " + e2.getMessage());
                return null;
            }
        }

        if (extraVarsMap == null || extraVarsMap.isEmpty()) {
            System.err.println("error encryptExtraVars, the given vars cannot be mapped to a valid map.");
            return null;
        }

        extraVarsMap.forEach((key, value) -> {
            try {
                String encryptedKey = encryptVariable(value, vaultPasswordFile);
                if (encryptedKey != null) {
                    encryptedExtraVarsMap.put(key, encryptedKey);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        });

        StringBuilder stringBuilder = new StringBuilder();
        encryptedExtraVarsMap.forEach((key, value) -> {
            stringBuilder.append(key).append(":");
            stringBuilder.append(" ").append(value).append("\n");
        });

        //return mapperYaml.writeValueAsString(encryptedExtraVarsMap);
        return stringBuilder.toString();
    }

    public void encryptFileAnsibleVault(File file) {

        List<String> procArgs = new ArrayList<>();
        procArgs.add("ansible-vault");
        procArgs.add("encrypt");
        procArgs.add(file.getAbsolutePath());

        // execute the ssh-agent add process
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(procArgs)
                .directory(baseDirectory.toFile());
        Process proc = null;

        try {
            proc = processBuilder.start();

            OutputStream stdin = proc.getOutputStream();
            OutputStreamWriter stdinw = new OutputStreamWriter(stdin);

            try {
                stdinw.write(generatedVaultPassword + "\n");
                stdinw.write(generatedVaultPassword + "\n");
                stdinw.flush();
            } catch (Exception e) {
                System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
            }

            int exitCode = proc.waitFor();

            if (exitCode != 0) {
                throw new AnsibleException("ERROR: encryptFileAnsibleVault:" + procArgs,
                        AnsibleException.AnsibleFailureReason.AnsibleNonZero);
            }


        } catch (Exception e) {
            System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
        } finally {
            // Make sure to always cleanup on failure and success
            if (proc != null) {
                proc.destroy();
            }
        }
    }

}