package com.rundeck.plugins.ansible.ansible;

import com.rundeck.plugins.ansible.util.Logging;
import com.rundeck.plugins.ansible.util.ListenerFactory;
import com.rundeck.plugins.ansible.util.Listener;
import com.rundeck.plugins.ansible.util.ArgumentTokenizer;
import com.dtolabs.rundeck.core.utils.SSHAgentProcess;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

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

  public static AnsibleRunner adHoc(String module, String arg) {
    AnsibleRunner ar = new AnsibleRunner(AnsibleCommand.AdHoc);
    ar.module = module;
    ar.arg = arg;
    return ar;
  }

  public static AnsibleRunner playbookPath(String playbook) {
    AnsibleRunner ar = new AnsibleRunner(AnsibleCommand.PlaybookPath);
    ar.playbook = playbook;
    return ar;
  }

  public static AnsibleRunner playbookInline(String playbook) {
    AnsibleRunner ar = new AnsibleRunner(AnsibleCommand.PlaybookInline);
    ar.playbook = playbook;
    return ar;
  }

  /**
   * Splits up a command and its arguments inf form of a string into a list of strings.
   * @param commandline  String with a possibly complex command and arguments
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

  private boolean done = false;

  private final AnsibleCommand type;

  private String playbook;
  private String inventory;
  private String module;
  private String arg;
  private String extraVars;
  private String extraParams;
  private String vaultPass;
  private boolean ignoreErrors = false;

  // ansible ssh args
  private boolean sshUsePassword = false;
  private String sshPass;
  private String sshUser;
  private String sshPrivateKey;
  private Integer sshTimeout;
  private boolean sshUseAgent = false;
  private String sshPassphrase;
  private SSHAgentProcess sshAgent;
  private Integer sshAgentTimeToLive = 0;

  // ansible become args
  protected Boolean become = Boolean.FALSE;
  protected String becomeMethod;
  protected String becomeUser;
  protected String becomePassword;

  private boolean debug = false;

  private Path baseDirectory;
  private Path ansibleBinariesDirectory;
  private boolean usingTempDirectory;
  private boolean retainTempDirectory;
  private final List<String> limits = new ArrayList<>();
  private int result;
  private Map<String, String> options = new HashMap<>();
  private String executable = "sh";

  protected String configFile;

  private Listener listener;

  private String generatedVaultPassword;

  private boolean encryptTempFiles = false;

  private AnsibleRunner(AnsibleCommand type) {
    this.type = type;
  }


  public AnsibleRunner setInventory(String inv) {
    if (inv != null && inv.length() > 0) {
      inventory = inv;
    }
    return this;
  }

  public AnsibleRunner limit(String host) {
    limits.add(host);
    return this;
  }

  public AnsibleRunner limit(Collection<String> hosts) {
    limits.addAll(hosts);
    return this;
  }

  /**
   * Additional arguments to pass to the process
   * @param args  extra commandline which gets appended to the base command and arguments
   */
  public AnsibleRunner extraParams(String params) {
	    if (params != null && params.length() > 0) {
	    	extraParams = params;
	    }
	    return this;
	  }

  public AnsibleRunner extraVars(String args) {
    if (args != null && args.length() > 0) {
    	extraVars = args;
    }
    return this;
  }

  /**
   * Add options passed as Environment variables to ansible
   */
  public AnsibleRunner options(Map<String, String> options) {
    this.options.putAll(options);
    return this;
  }

  /**
   * Vault Password
   * @param pass  vault password to be used to decrypt group variables
   */
  public AnsibleRunner vaultPass(String pass) {
    if (pass != null && pass.length() > 0) {
      vaultPass = pass;
    }
    return this;
  }

  public AnsibleRunner ignoreErrors(boolean ignoreErrors) {
	  this.ignoreErrors = ignoreErrors;
	  return this;
  }

  public AnsibleRunner sshUser(String user) {
    if (user != null && user.length() > 0) {
      sshUser = user;
    }
    return this;
  }

  public AnsibleRunner sshPass(String pass) {
    if (pass != null && pass.length() > 0) {
      sshPass = pass;
    }
    return this;
  }

  public AnsibleRunner sshPrivateKey(String key) {
    if (key != null && key.length() > 0) {
      sshPrivateKey = key;
    }
    return this;
  }

  public AnsibleRunner sshUsePassword(Boolean usePass) {
    if (usePass != null) {
      sshUsePassword = usePass;
    } else {
      sshUsePassword = false;
    }
    return this;
  }

  public AnsibleRunner sshUseAgent(Boolean useAgent) {
    if (useAgent != null) {
      sshUseAgent = useAgent;
    } else {
      sshUseAgent = false;
    }
    return this;
  }

  public AnsibleRunner sshPassphrase(String passphrase) {
    if (passphrase != null && passphrase.length() > 0) {
      sshPassphrase = passphrase;
    }
    return this;
  }

  public AnsibleRunner sshTimeout(Integer timeout) {
    if (timeout != null && timeout  > 0) {
      sshTimeout = timeout;
    }
    return this;
  }

  public AnsibleRunner become(Boolean useBecome) {
    if (useBecome != null) {
      become = useBecome;
    } else {
      become = false;
    }
    return this;
  }

  public AnsibleRunner becomeMethod(String method) {
    if (method != null && method.length() > 0) {
      becomeMethod = method;
    }
    return this;
  }

  public AnsibleRunner becomeUser(String user) {
    if (user != null && user.length() > 0) {
      becomeUser = user;
    }
    return this;
  }

  public AnsibleRunner becomePassword(String pass) {
    if (pass != null && pass.length() > 0) {
      becomePassword = pass;
    }
    return this;
  }

  public AnsibleRunner configFile(String path) {
    if (path != null && path.length() > 0) {
      configFile = path;
    }
    return this;
  }

  /**
   * Set the listener to notify, when run in stream mode, see {@link #stream()}
   * @param listener  the listener which will receive output lines
   */
  public AnsibleRunner listener(Listener listener) {
    this.listener = listener;
    return this;
  }

  /**
   * Run Ansible with -vvvv and print the command and output to the console / log
   */
  public AnsibleRunner debug() {
    return debug(true);
  }

  /**
   * Run Ansible with -vvvv and print the command and output to the console / log
   */
  public AnsibleRunner debug(boolean debug) {
    this.debug = debug;
    return this;
  }

  /**
   * Keep the temp dir around, dont' delete it.
   */
  public AnsibleRunner retainTempDirectory() {
    return retainTempDirectory(true);
  }

  /**
   * Keep the temp dir around, dont' delete it.
   */
  public AnsibleRunner retainTempDirectory(boolean retainTempDirectory) {
    this.retainTempDirectory = retainTempDirectory;
    return this;
  }

  /**
   * Specify in which directory Ansible is run, noting it is a temporary directory.
   */
  public AnsibleRunner tempDirectory(Path dir) {
    if (dir != null) {
      this.baseDirectory = dir;
      this.usingTempDirectory = true;
    }
    return this;
  }

  /**
   * Specify in which directory Ansible is run.
   * A temporary directory will be used if none is specified.
   */
  public AnsibleRunner baseDirectory(String dir) {
    if (dir != null) {
      this.baseDirectory = Paths.get(dir);
    }
    return this;
  }

  public AnsibleRunner ansibleBinariesDirectory(String dir) {
    if (dir != null) {
      this.ansibleBinariesDirectory = Paths.get(dir);
    }
    return this;
  }

  /**
   * Specify the executable
   */
  public AnsibleRunner executable(String executable) {
    this.executable = executable;
    return this;
  }

  public AnsibleRunner encryptTemporaryFiles(boolean encryptVarsFiles){
    this.encryptTempFiles = encryptVarsFiles;
    return this;
  }

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
    File tempVaultFile = null;
    File tempPkFile = null;
    File tempVarsFile = null;

    List<String> procArgs = new ArrayList<>();

    if(encryptTempFiles){
      UUID uuid = UUID.randomUUID();
      generatedVaultPassword = uuid.toString();

      tempVaultFile = File.createTempFile("ansible-runner", "internal-vault");
      Files.write(tempVaultFile.toPath(), generatedVaultPassword.getBytes());
      procArgs.add("--vault-password-file" + "=" + tempVaultFile.getAbsolutePath());
    }

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

      if (arg != null && arg.length() > 0) {
        procArgs.add("-a");
        procArgs.add(arg);
      }
      procArgs.add("-t");
      procArgs.add(baseDirectory.toFile().getAbsolutePath());
    } else if (type == AnsibleCommand.PlaybookPath) {
      procArgs.add(playbook);
    } else if (type == AnsibleCommand.PlaybookInline) {

	  tempPlaybook = File.createTempFile("ansible-runner", "playbook");
	  Files.write(tempPlaybook.toPath(), playbook.toString().getBytes());
	  procArgs.add(tempPlaybook.getAbsolutePath());
    }

    if (inventory != null && inventory.length() > 0) {
      procArgs.add("--inventory-file" + "=" + inventory);
    }

    if (limits != null && limits.size() == 1) {
      procArgs.add("-l");
      procArgs.add(limits.get(0));

    } else if (limits != null && limits.size() > 1) {
      tempFile = File.createTempFile("ansible-runner", "targets");
      StringBuilder sb = new StringBuilder();
      for (String limit : limits) {
        sb.append(limit).append("\n");
      }
      Files.write(tempFile.toPath(), sb.toString().getBytes());

      procArgs.add("-l");
      procArgs.add("@" + tempFile.getAbsolutePath());
    }

    if (debug == Boolean.TRUE) {
      procArgs.add("-vvv");
    }

    if (extraVars != null && extraVars.length() > 0) {
    	tempVarsFile = this.createTemporaryFile("extra-vars",extraVars  , encryptTempFiles);
                //File.createTempFile("ansible-runner", "extra-vars");
    	//Files.write(tempVarsFile.toPath(), extraVars.getBytes());
        procArgs.add("--extra-vars" + "=" + "@" + tempVarsFile.getAbsolutePath());
    }

    if (vaultPass != null && vaultPass.length() > 0) {
      tempVaultFile = File.createTempFile("ansible-runner", "vault");
      Files.write(tempVaultFile.toPath(), vaultPass.getBytes());
      procArgs.add("--vault-password-file" + "=" + tempVaultFile.getAbsolutePath());
    }

    if (sshPrivateKey != null && sshPrivateKey.length() > 0) {

      String privateKeyData = sshPrivateKey.replaceAll("\r\n","\n");
      tempPkFile = this.createTemporaryFile("id_rsa",privateKeyData  , false);

      // Only the owner can read and write
      Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(tempPkFile.toPath(), perms);

      if(sshUseAgent){
        registerKeySshAgent(tempPkFile.getAbsolutePath());
      }

      if(encryptTempFiles){
        encryptFileAnsibleVault(tempPkFile);
      }
      procArgs.add("--private-key" + "=" + tempPkFile.toPath());
    }

    if (sshUser != null && sshUser.length() > 0) {
       procArgs.add("--user" + "=" + sshUser);
    }

    if (sshUsePassword) {
       procArgs.add("--ask-pass");
    }

    if (sshTimeout != null && sshTimeout  > 0) {
      procArgs.add("--timeout" + "=" + sshTimeout);
    }

    if (become == true) {
       procArgs.add("--become");
       if (becomePassword != null && becomePassword.length() > 0) {
         procArgs.add("--ask-become-pass");
       }
    }

    if (becomeMethod != null && becomeMethod.length() > 0) {
       procArgs.add("--become-method" + "=" + becomeMethod);
    }

    if (becomeUser != null && becomeUser.length() > 0) {
       procArgs.add("--become-user" + "=" + becomeUser);
    }

    // default the listener to stdout logger
    if (listener == null) {
        listener = ListenerFactory.getListener(System.out);
    }

    if (extraParams != null && extraParams.length() > 0) {
        procArgs.addAll(tokenizeCommand(extraParams));
    }

    if (debug) {
        System.out.println(" procArgs: " +  procArgs);
    }

    // execute the ansible process
    ProcessBuilder processBuilder = new ProcessBuilder()
      .command(procArgs)
      .directory(baseDirectory.toFile()); // set cwd
    Process proc = null;

    Map<String, String> processEnvironment = processBuilder.environment();

    if (configFile != null && configFile.length() > 0) {
      if (debug) {
        System.out.println(" ANSIBLE_CONFIG: "+configFile);
      }

      processEnvironment.put("ANSIBLE_CONFIG", configFile);
    }

    for (String optionName : this.options.keySet()) {
        processEnvironment.put(optionName, this.options.get(optionName));
    }

    if(sshUseAgent && sshAgent!=null){
      processEnvironment.put("SSH_AUTH_SOCK", this.sshAgent.getSocketPath());
    }

    try {
      proc = processBuilder.start();
      OutputStream stdin = proc.getOutputStream();
      OutputStreamWriter stdinw = new OutputStreamWriter(stdin);

      if (sshUsePassword) {
         if (sshPass != null && sshPass.length() > 0) {
        	 stdinw.write(sshPass+"\n");
        	 stdinw.flush();
         } else {
            throw new AnsibleException("Missing ssh password.",AnsibleException.AnsibleFailureReason.AnsibleNonZero);
         }
      }

      if (become) {
         if (becomePassword != null && becomePassword.length() > 0) {
        	 stdinw.write(becomePassword+"\n");
        	 stdinw.flush();
         }
      }

      stdinw.close();
      Thread errthread = Logging.copyStreamThread(proc.getErrorStream(), listener);
      Thread outthread = Logging.copyStreamThread(proc.getInputStream(), listener);
      errthread.start();
      outthread.start();
      result = proc.waitFor();
      outthread.join();
      errthread.join();
      System.err.flush();
      System.out.flush();


      if(sshUseAgent){
        if(sshAgent!=null){
          sshAgent.stopAgent();
        }
      }

      if (result != 0) {
    	  if (ignoreErrors == false) {
              throw new AnsibleException("ERROR: Ansible execution returned with non zero code.",
        		                      AnsibleException.AnsibleFailureReason.AnsibleNonZero);
    	  }
      }
    } catch (InterruptedException e) {
        if(proc!=null) {
          proc.destroy();
        }
        Thread.currentThread().interrupt();
        throw new AnsibleException("ERROR: Ansible Execution Interrupted.", e, AnsibleException.AnsibleFailureReason.Interrupted);
    } catch (IOException e) {
        throw new AnsibleException("ERROR: Ansible IO failure: "+e.getMessage(), e, AnsibleException.AnsibleFailureReason.IOFailure);
    } catch (AnsibleException e) {
        throw e;
    } catch (Exception e) {
        if(proc!=null) {
          proc.destroy();
        }
        throw new AnsibleException("ERROR: Ansible execution returned with non zero code.", e, AnsibleException.AnsibleFailureReason.Unknown);
    } finally {
        // Make sure to always cleanup on failure and success
        if(proc!=null) {
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
        if (tempVaultFile != null && !tempVaultFile.delete()) {
          tempVaultFile.deleteOnExit();
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

  public int getResult() {
    return result;
  }

  public boolean registerKeySshAgent(String keyPath) throws AnsibleException, Exception {

    if(sshAgent==null){
      sshAgent = new SSHAgentProcess(this.sshAgentTimeToLive);
    }

    List<String> procArgs = new ArrayList<>();
    procArgs.add("/usr/bin/ssh-add");
    procArgs.add(keyPath);

    if (debug) {
      System.out.println("ssh-agent socket " + sshAgent.getClass());
      System.out.println(" registerKeySshAgent: "+procArgs.toString());
    }

    // execute the ssh-agent add process
    ProcessBuilder processBuilder = new ProcessBuilder()
            .command(procArgs)
            .redirectErrorStream(true)
            .directory(baseDirectory.toFile());

    Process proc = null;

    Map<String, String> env = processBuilder.environment();
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

    try {
      proc = processBuilder.start();

      OutputStream stdin = proc.getOutputStream();
      OutputStreamWriter stdinw = new OutputStreamWriter(stdin);

      try{
        if (sshPassphrase != null && sshPassphrase.length() > 0) {
          stdinw.write(sshPassphrase+"\n");
          stdinw.flush();
        }
      } catch (Exception  e) {
        if (debug) {
          System.out.println("not prompt enable");
        }
      }

      stdinw.close();
      stdin.close();

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
          throw new AnsibleException("ERROR: ssh-add returns with non zero code:" + procArgs.toString(),
                  AnsibleException.AnsibleFailureReason.AnsibleNonZero);
      }


    } catch (IOException  e) {
      throw new AnsibleException("ERROR: error adding private key to ssh-agent." + procArgs.toString(), e, AnsibleException.AnsibleFailureReason.Unknown);
    } catch (InterruptedException e) {
      if(proc!=null) {
        proc.destroy();
      }
      Thread.currentThread().interrupt();
      throw new AnsibleException("ERROR: error adding private key to ssh-agent Interrupted.", e, AnsibleException.AnsibleFailureReason.Interrupted);
    }finally {
      // Make sure to always cleanup on failure and success
      if(proc!=null) {
        proc.destroy();
      }

      if(tempPassVarsFile!=null && !tempPassVarsFile.delete()){
        tempPassVarsFile.deleteOnExit();
      }
    }

    return true;
  }


  public File createTemporaryFile(String suffix, String data, boolean encrypt) throws IOException {
    File tempVarsFile = File.createTempFile("ansible-runner", suffix);
    Files.write(tempVarsFile.toPath(), data.getBytes());

    if(encrypt){
        encryptFileAnsibleVault(tempVarsFile);
    }

    return tempVarsFile;

  }

  public void encryptFileAnsibleVault(File file){

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
        throw new AnsibleException("ERROR: encryptFileAnsibleVault:" + procArgs.toString(),
                AnsibleException.AnsibleFailureReason.AnsibleNonZero);
      }


    } catch (Exception e) {
      System.err.println("error encryptFileAnsibleVault file " + e.getMessage());
    }finally {
        // Make sure to always cleanup on failure and success
        if(proc!=null) {
            proc.destroy();
        }
    }
  }

}
