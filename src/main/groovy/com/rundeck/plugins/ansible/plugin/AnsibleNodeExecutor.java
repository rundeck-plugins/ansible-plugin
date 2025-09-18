package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.rundeck.plugins.ansible.ansible.*;
import com.rundeck.plugins.ansible.ansible.AnsibleRunnerContextBuilder;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.service.NodeExecutor;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResultImpl;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.rundeck.plugins.ansible.util.AnsibleUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(name = AnsibleNodeExecutor.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.NodeExecutor)
public class AnsibleNodeExecutor implements NodeExecutor, AnsibleDescribable, ProxyRunnerPlugin {

  public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsibleNodeExecutor";

  public static Description DESC = null;

  static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible Ad-Hoc Node Executor");
        builder.description("Runs Ansible Ad-Hoc commands on the nodes using the shell module.");
        builder.property(BINARIES_DIR_PATH_PROP);
        builder.property(EXECUTABLE_PROP);
        builder.property(WINDOWS_EXECUTABLE_PROP);
        builder.property(CONFIG_FILE_PATH);
        builder.property(GENERATE_INVENTORY_PROP);
        builder.property(SSH_AUTH_TYPE_PROP);
        builder.property(SSH_USER_PROP);
        builder.property(SSH_PASSWORD_STORAGE_PROP);
        builder.property(SSH_KEY_FILE_PROP);
        builder.property(SSH_KEY_STORAGE_PROP);
        builder.property(SSH_TIMEOUT_PROP);
        builder.property(SSH_USE_AGENT);
        builder.property(SSH_PASSPHRASE);
        builder.property(SSH_PASSPHRASE_OPTION);
        builder.property(BECOME_PROP);
        builder.property(BECOME_AUTH_TYPE_PROP);
        builder.property(BECOME_USER_PROP);
        builder.property(BECOME_PASSWORD_STORAGE_PROP);
        builder.property(VAULT_KEY_FILE_PROP);
        builder.property(VAULT_KEY_STORAGE_PROP);


        builder.mapping(ANSIBLE_BINARIES_DIR_PATH,PROJ_PROP_PREFIX + ANSIBLE_BINARIES_DIR_PATH);
        builder.frameworkMapping(ANSIBLE_BINARIES_DIR_PATH,FWK_PROP_PREFIX + ANSIBLE_BINARIES_DIR_PATH);
        builder.mapping(ANSIBLE_EXECUTABLE,PROJ_PROP_PREFIX + ANSIBLE_EXECUTABLE);
        builder.frameworkMapping(ANSIBLE_EXECUTABLE,FWK_PROP_PREFIX + ANSIBLE_EXECUTABLE);
        builder.mapping(ANSIBLE_WINDOWS_EXECUTABLE,PROJ_PROP_PREFIX + ANSIBLE_WINDOWS_EXECUTABLE);
        builder.frameworkMapping(ANSIBLE_WINDOWS_EXECUTABLE,FWK_PROP_PREFIX + ANSIBLE_WINDOWS_EXECUTABLE);
        builder.mapping(ANSIBLE_CONFIG_FILE_PATH,PROJ_PROP_PREFIX + ANSIBLE_CONFIG_FILE_PATH);
        builder.frameworkMapping(ANSIBLE_CONFIG_FILE_PATH,FWK_PROP_PREFIX + ANSIBLE_CONFIG_FILE_PATH);
        builder.mapping(ANSIBLE_BASE_DIR_PATH,PROJ_PROP_PREFIX + ANSIBLE_BASE_DIR_PATH);
        builder.frameworkMapping(ANSIBLE_BASE_DIR_PATH,FWK_PROP_PREFIX + ANSIBLE_BASE_DIR_PATH);
        builder.mapping(ANSIBLE_GENERATE_INVENTORY,PROJ_PROP_PREFIX + ANSIBLE_GENERATE_INVENTORY);
        builder.frameworkMapping(ANSIBLE_GENERATE_INVENTORY,FWK_PROP_PREFIX + ANSIBLE_GENERATE_INVENTORY);
        builder.mapping(ANSIBLE_SSH_AUTH_TYPE,PROJ_PROP_PREFIX + ANSIBLE_SSH_AUTH_TYPE);
        builder.frameworkMapping(ANSIBLE_SSH_AUTH_TYPE,FWK_PROP_PREFIX + ANSIBLE_SSH_AUTH_TYPE);
        builder.mapping(ANSIBLE_SSH_USER,PROJ_PROP_PREFIX + ANSIBLE_SSH_USER);
        builder.frameworkMapping(ANSIBLE_SSH_USER,FWK_PROP_PREFIX + ANSIBLE_SSH_USER);
        builder.mapping(ANSIBLE_SSH_TIMEOUT,PROJ_PROP_PREFIX + ANSIBLE_SSH_TIMEOUT);
        builder.frameworkMapping(ANSIBLE_SSH_TIMEOUT,FWK_PROP_PREFIX + ANSIBLE_SSH_TIMEOUT);
        builder.mapping(ANSIBLE_SSH_KEYPATH,PROJ_PROP_PREFIX + ANSIBLE_SSH_KEYPATH);
        builder.frameworkMapping(ANSIBLE_SSH_KEYPATH,FWK_PROP_PREFIX + ANSIBLE_SSH_KEYPATH);
        builder.mapping(ANSIBLE_SSH_KEYPATH_STORAGE_PATH,PROJ_PROP_PREFIX + ANSIBLE_SSH_KEYPATH_STORAGE_PATH);
        builder.frameworkMapping(ANSIBLE_SSH_KEYPATH_STORAGE_PATH,FWK_PROP_PREFIX + ANSIBLE_SSH_KEYPATH_STORAGE_PATH);
        builder.mapping(ANSIBLE_SSH_PASSWORD_STORAGE_PATH,PROJ_PROP_PREFIX + ANSIBLE_SSH_PASSWORD_STORAGE_PATH);
        builder.frameworkMapping(ANSIBLE_SSH_PASSWORD_STORAGE_PATH,FWK_PROP_PREFIX + ANSIBLE_SSH_PASSWORD_STORAGE_PATH);
        builder.mapping(ANSIBLE_SSH_USE_AGENT,PROJ_PROP_PREFIX + ANSIBLE_SSH_USE_AGENT);
        builder.frameworkMapping(ANSIBLE_SSH_USE_AGENT,FWK_PROP_PREFIX + ANSIBLE_SSH_USE_AGENT);
        builder.mapping(ANSIBLE_SSH_PASSPHRASE_OPTION,PROJ_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE_OPTION);
        builder.frameworkMapping(ANSIBLE_SSH_PASSPHRASE_OPTION,FWK_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE_OPTION);
        builder.mapping(ANSIBLE_SSH_PASSPHRASE,PROJ_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE);
        builder.frameworkMapping(ANSIBLE_SSH_PASSPHRASE,FWK_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE);
        builder.mapping(ANSIBLE_BECOME,PROJ_PROP_PREFIX + ANSIBLE_BECOME);
        builder.frameworkMapping(ANSIBLE_BECOME,FWK_PROP_PREFIX + ANSIBLE_BECOME);
        builder.mapping(ANSIBLE_BECOME_USER,PROJ_PROP_PREFIX + ANSIBLE_BECOME_USER);
        builder.frameworkMapping(ANSIBLE_BECOME_USER,FWK_PROP_PREFIX + ANSIBLE_BECOME_USER);
        builder.mapping(ANSIBLE_BECOME_METHOD,PROJ_PROP_PREFIX + ANSIBLE_BECOME_METHOD);
        builder.frameworkMapping(ANSIBLE_BECOME_METHOD,FWK_PROP_PREFIX + ANSIBLE_BECOME_METHOD);
        builder.mapping(ANSIBLE_BECOME_PASSWORD_STORAGE_PATH,PROJ_PROP_PREFIX + ANSIBLE_BECOME_PASSWORD_STORAGE_PATH);
        builder.frameworkMapping(ANSIBLE_BECOME_PASSWORD_STORAGE_PATH,FWK_PROP_PREFIX + ANSIBLE_BECOME_PASSWORD_STORAGE_PATH);
        builder.mapping(ANSIBLE_VAULT_PATH,PROJ_PROP_PREFIX + ANSIBLE_VAULT_PATH);
        builder.frameworkMapping(ANSIBLE_VAULT_PATH,FWK_PROP_PREFIX + ANSIBLE_VAULT_PATH);
        builder.mapping(ANSIBLE_VAULTSTORE_PATH,PROJ_PROP_PREFIX + ANSIBLE_VAULTSTORE_PATH);
        builder.frameworkMapping(ANSIBLE_VAULTSTORE_PATH,FWK_PROP_PREFIX + ANSIBLE_VAULTSTORE_PATH);

      DESC=builder.build();
  }

  @Override
  public NodeExecutorResult executeCommand(ExecutionContext context, String[] command, INodeEntry node) {

    AnsibleRunner runner = null;

    StringBuilder cmdArgs = new StringBuilder();

    //check if the node is a windows host
    boolean windows = false;
    if (null != node.getAttributes()) {
      String osFamily = node.getAttributes().get("osFamily");
      if (null != osFamily) {
        windows=osFamily.toLowerCase().contains("windows");
      }
    }

    String executable = PropertyResolver.resolveProperty(
                          AnsibleDescribable.ANSIBLE_EXECUTABLE,
                          AnsibleDescribable.DEFAULT_ANSIBLE_EXECUTABLE,
                          context.getFrameworkProject(),
                          context.getFramework(),
                          node,
                          null
                        );

    //windows executable
    String windowsExecutable = PropertyResolver.resolveProperty(
                                      AnsibleDescribable.ANSIBLE_WINDOWS_EXECUTABLE,
                                      AnsibleDescribable.DEFAULT_ANSIBLE_WINDOWS_EXECUTABLE,
                                      context.getFrameworkProject(),
                                      context.getFramework(),
                                      node,
                                      null
                              );


    if(windows) {
        cmdArgs.append("executable=").append(windowsExecutable);
        for (String cmd : command) {
            cmdArgs.append(" ").append(cmd).append("");
        }
    }else{
        cmdArgs.append("executable=").append(executable);
        for (String cmd : command) {
            cmdArgs.append(" '").append(cmd).append("'");
        }
    }


    Map<String, Object> jobConf = new HashMap<String, Object>();

    if(windows){
        //for windows host, the shell must be win_shell
        //look: http://docs.ansible.com/ansible/intro_windows.html
        jobConf.put(AnsibleDescribable.ANSIBLE_MODULE,"win_shell");
    }else{
        jobConf.put(AnsibleDescribable.ANSIBLE_MODULE,"shell");
    }

    jobConf.put(AnsibleDescribable.ANSIBLE_MODULE_ARGS,cmdArgs.toString());
    jobConf.put(AnsibleDescribable.ANSIBLE_LIMIT,node.getNodename());

    if ("true".equals(System.getProperty("ansible.debug"))) {
      jobConf.put(AnsibleDescribable.ANSIBLE_DEBUG,"True");
    } else {
      jobConf.put(AnsibleDescribable.ANSIBLE_DEBUG,"False");
    }


    AnsibleRunnerContextBuilder contextBuilder = new AnsibleRunnerContextBuilder(node, context, context.getFramework(), jobConf);

    try {
        runner = AnsibleRunner.buildAnsibleRunner(contextBuilder);
        runner.setCustomTmpDirPath(AnsibleUtil.getCustomTmpPathDir(contextBuilder.getFramework()));
    } catch (ConfigurationException e) {
          return NodeExecutorResultImpl.createFailure(AnsibleException.AnsibleFailureReason.ParseArgumentsError, e.getMessage(), node);
    }

    try {
        runner.run();
    } catch (Exception e) {
        return NodeExecutorResultImpl.createFailure(AnsibleException.AnsibleFailureReason.AnsibleError, e.getMessage(), node);
    }finally {
        contextBuilder.cleanupTempFiles();
    }

    return NodeExecutorResultImpl.createSuccess(node);
  }

  @Override
  public Description getDescription() {
    return DESC;
    }

    @Override
    public List<String> listSecretsPath(ExecutionContext context, INodeEntry node) {
        Map<String, Object> jobConf = new HashMap<>();
        jobConf.put(AnsibleDescribable.ANSIBLE_LIMIT,node.getNodename());
        AnsibleRunnerContextBuilder builder = new AnsibleRunnerContextBuilder(node, context, context.getFramework(), jobConf);

        return AnsibleUtil.getSecretsPath(builder);
    }
}

