package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable;
import com.rundeck.plugins.ansible.ansible.AnsibleException.AnsibleFailureReason;
import com.rundeck.plugins.ansible.ansible.AnsibleRunner;
import com.rundeck.plugins.ansible.ansible.AnsibleRunnerBuilder;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.IRundeckProject;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.impl.jsch.JschScpFileCopier;
import com.dtolabs.rundeck.core.execution.service.FileCopier;
import com.dtolabs.rundeck.core.execution.service.FileCopierException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.rundeck.plugins.ansible.util.AnsibleUtil;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(name = AnsibleFileCopier.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.FileCopier)
public class AnsibleFileCopier implements FileCopier, AnsibleDescribable, ProxyRunnerPlugin {

  public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsibleFileCopier";

  public static Description DESC = null;

  static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible File Copier");
        builder.description("Sends a file to a node via the copy module.");
        builder.property(BINARIES_DIR_PATH_PROP);
        builder.property(INVENTORY_INLINE_PROP);
        builder.property(CONFIG_FILE_PATH);
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
        builder.property(CONFIG_ENCRYPT_TEMP_FILES);

        builder.mapping(ANSIBLE_CONFIG_FILE_PATH,PROJ_PROP_PREFIX + ANSIBLE_CONFIG_FILE_PATH);
        builder.frameworkMapping(ANSIBLE_CONFIG_FILE_PATH,FWK_PROP_PREFIX + ANSIBLE_CONFIG_FILE_PATH);
        builder.mapping(ANSIBLE_VAULT_PATH,PROJ_PROP_PREFIX + ANSIBLE_VAULT_PATH);
        builder.frameworkMapping(ANSIBLE_VAULT_PATH,FWK_PROP_PREFIX + ANSIBLE_VAULT_PATH);
        builder.mapping(ANSIBLE_VAULTSTORE_PATH,PROJ_PROP_PREFIX + ANSIBLE_VAULTSTORE_PATH);
        builder.frameworkMapping(ANSIBLE_VAULTSTORE_PATH,FWK_PROP_PREFIX + ANSIBLE_VAULTSTORE_PATH);
        builder.mapping(ANSIBLE_SSH_PASSPHRASE,PROJ_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE);
        builder.frameworkMapping(ANSIBLE_SSH_PASSPHRASE,FWK_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE);
        builder.mapping(ANSIBLE_SSH_PASSPHRASE_OPTION,PROJ_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE_OPTION);
        builder.frameworkMapping(ANSIBLE_SSH_PASSPHRASE_OPTION,FWK_PROP_PREFIX + ANSIBLE_SSH_PASSPHRASE_OPTION);
        builder.mapping(ANSIBLE_SSH_USE_AGENT,PROJ_PROP_PREFIX + ANSIBLE_SSH_USE_AGENT);
        builder.frameworkMapping(ANSIBLE_SSH_USE_AGENT,FWK_PROP_PREFIX + ANSIBLE_SSH_USE_AGENT);
        builder.mapping(ENCRYPT_TEMP_FILES,PROJ_PROP_PREFIX + ENCRYPT_TEMP_FILES);
        builder.frameworkMapping(ENCRYPT_TEMP_FILES,FWK_PROP_PREFIX + ENCRYPT_TEMP_FILES);

        DESC=builder.build();
  }

  @Override
  public String copyFileStream(ExecutionContext context, InputStream input, INodeEntry node, String destination) throws FileCopierException {
    return doFileCopy(context, null, input, null, node, destination);
  }

  @Override
  public String copyFile(ExecutionContext context, File file, INodeEntry node, String destination) throws FileCopierException {
    return doFileCopy(context, file, null, null, node, destination);
  }

  @Override
  public String copyScriptContent(ExecutionContext context, String script, INodeEntry node, String destination) throws FileCopierException {
    return doFileCopy(context, null, null, script, node, destination);
  }

  private String doFileCopy(
    final ExecutionContext context,
    final File scriptFile,
    final InputStream input,
    final String script,
    final INodeEntry node,
    String destinationPath
  ) throws FileCopierException {

    AnsibleRunner runner = null;

    //check if the node is a windows host
    boolean windows = false;
    if (null != node.getAttributes()) {
      String osFamily = node.getAttributes().get("osFamily");
      if (null != osFamily) {
        windows=osFamily.toLowerCase().contains("windows");
      }
    }

    IRundeckProject project = context.getFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject());

    if (destinationPath == null) {
      String identity = (context.getDataContext() != null && context.getDataContext().get("job") != null) ?
                        context.getDataContext().get("job").get("execid") : null;

      destinationPath = JschScpFileCopier.generateRemoteFilepathForNode(
        node,
        project,
        context.getFramework(),
        scriptFile != null ? scriptFile.getName() : "dispatch-script",
        null,
        identity
      );
    }

    File localTempFile = scriptFile != null ?
      scriptFile : JschScpFileCopier.writeTempFile(context, null, input, script);

    String cmdArgs = "src='" + localTempFile.getAbsolutePath() + "' dest='" + destinationPath + "'";

    Map<String, Object> jobConf = new HashMap<String, Object>();

    if(windows){
        jobConf.put(AnsibleDescribable.ANSIBLE_MODULE,"win_copy");
    }else{
        jobConf.put(AnsibleDescribable.ANSIBLE_MODULE,"copy");
    }

    jobConf.put(AnsibleDescribable.ANSIBLE_MODULE_ARGS,cmdArgs.toString());
    jobConf.put(AnsibleDescribable.ANSIBLE_LIMIT,node.getNodename());

    if ("true".equals(System.getProperty("ansible.debug"))) {
      jobConf.put(AnsibleDescribable.ANSIBLE_DEBUG,"True");
    } else {
      jobConf.put(AnsibleDescribable.ANSIBLE_DEBUG,"False");
    }

    AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(node, context, context.getFramework(), jobConf);


    try {
        runner = builder.buildAnsibleRunner();
    } catch (ConfigurationException e) {
          throw new FileCopierException("Error configuring Ansible.",AnsibleFailureReason.ParseArgumentsError, e);
    }

    try {
          runner.run();
    } catch (Exception e) {
          throw new FileCopierException("Error running Ansible.", AnsibleFailureReason.AnsibleError, e);
    }

    builder.cleanupTempFiles();

    return destinationPath;
  }

  @Override
  public Description getDescription() {
    return DESC;
  }

    @Override
    public List<String> listSecretsPath(ExecutionContext context, INodeEntry node) {
        Map<String, Object> jobConf = new HashMap<>();
        jobConf.put(AnsibleDescribable.ANSIBLE_LIMIT,node.getNodename());
        AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(node, context, context.getFramework(), jobConf);

        return AnsibleUtil.getSecretsPath(builder);
    }
}

