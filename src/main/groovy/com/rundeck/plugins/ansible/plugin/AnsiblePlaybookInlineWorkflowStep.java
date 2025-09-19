package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin;
import com.dtolabs.rundeck.plugins.config.ConfiguredBy;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable;
import com.rundeck.plugins.ansible.ansible.AnsibleException;
import com.rundeck.plugins.ansible.ansible.AnsibleRunner;
import com.rundeck.plugins.ansible.ansible.AnsibleRunnerContextBuilder;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.rundeck.plugins.ansible.util.AnsibleUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Plugin(name = AnsiblePlaybookInlineWorkflowStep.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class AnsiblePlaybookInlineWorkflowStep implements StepPlugin, AnsibleDescribable, ProxyRunnerPlugin, ConfiguredBy<AnsiblePluginGroup> {

    public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsiblePlaybookInlineWorkflowStep";

    public static Description DESC = null;

    private AnsiblePluginGroup pluginGroup;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.pluginGroup(AnsiblePluginGroup.class);
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible Playbook Inline");
        builder.description("Runs an Inline Ansible Playbook.");

        builder.property(BINARIES_DIR_PATH_PROP);
        builder.property(BASE_DIR_PROP);
        builder.property(PLAYBOOK_INLINE_PROP);
        builder.property(EXTRA_VARS_PROP);
        builder.property(CONFIG_ENCRYPT_EXTRA_VARS);
        builder.property(INVENTORY_INLINE_PROP);
        builder.property(VAULT_KEY_FILE_PROP);
        builder.property(VAULT_KEY_STORAGE_PROP);
        builder.property(EXTRA_ATTRS_PROP);
        builder.property(SSH_AUTH_TYPE_PROP);
        builder.property(SSH_USER_PROP);
        builder.property(SSH_PASSWORD_STORAGE_PROP);
        builder.property(SSH_KEY_FILE_PROP);
        builder.property(SSH_KEY_STORAGE_PROP);
        builder.property(SSH_USE_AGENT);
        builder.property(SSH_PASSPHRASE);
        builder.property(SSH_PASSPHRASE_OPTION);
        builder.property(SSH_TIMEOUT_PROP);
        builder.property(BECOME_PROP);
        builder.property(BECOME_AUTH_TYPE_PROP);
        builder.property(BECOME_USER_PROP);
        builder.property(BECOME_PASSWORD_STORAGE_PROP);
        builder.property(DISABLE_LIMIT_PROP);

        builder.mapping(ANSIBLE_BASE_DIR_PATH,PROJ_PROP_PREFIX + ANSIBLE_BASE_DIR_PATH);
        builder.frameworkMapping(ANSIBLE_BASE_DIR_PATH,FWK_PROP_PREFIX + ANSIBLE_BASE_DIR_PATH);

        DESC = builder.build();
    }

    @Override
    public void executeStep(PluginStepContext context, Map<String, Object> configuration) throws StepException {

        AnsibleRunner runner = null;

        // set targets
        StringBuilder nodes = new StringBuilder();
        for (String node : context.getNodes().getNodeNames()) {
            nodes.append(node);
            nodes.append(",");
        }
        String limit = nodes.length() > 0 ? nodes.substring(0, nodes.length() - 1) : "";
        if (!limit.isEmpty()) {
            configuration.put(AnsibleDescribable.ANSIBLE_LIMIT, limit);
        }
        // set log level
        if (context.getDataContext().get("job").get("loglevel").equals("DEBUG")) {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG, "True");
        } else {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG, "False");
        }

        AnsibleRunnerContextBuilder contextBuilder = new AnsibleRunnerContextBuilder(context.getExecutionContext(),
                context.getFramework(),
                context.getNodes(),
                configuration,
                pluginGroup);

        try {
            runner = AnsibleRunner.buildAnsibleRunner(contextBuilder);
            runner.setCustomTmpDirPath(AnsibleUtil.getCustomTmpPathDir(contextBuilder.getFramework()));
        } catch (ConfigurationException e) {
            throw new StepException("Error configuring Ansible runner: " + e.getMessage(), e, AnsibleException.AnsibleFailureReason.ParseArgumentsError);
        }

        // ansible runner will take care of handling exceptions, here handle only jobs specific stuff
        try {
            runner.run();
        } catch (AnsibleException e) {
            Map<String, Object> failureData = new HashMap<>();
            failureData.put("message", e.getMessage());
            failureData.put("ansible-config", contextBuilder.getConfigFile());

            throw new StepException(e.getMessage(), e, e.getFailureReason(), failureData);
        } catch (Exception e) {
            Map<String, Object> failureData = new HashMap<>();
            failureData.put("message", e.getMessage());
            failureData.put("ansible-config", contextBuilder.getConfigFile());


            throw new StepException(e.getMessage(), e, AnsibleException.AnsibleFailureReason.AnsibleError, failureData);
        }finally {
            contextBuilder.cleanupTempFiles();
        }
    }



    @Override
    public Description getDescription() {
        return DESC;
    }


    @Override
    public List<String> listSecretsPathWorkflowStep(ExecutionContext context, Map<String, Object> configuration) {
        AnsibleRunnerContextBuilder builder = new AnsibleRunnerContextBuilder(context, context.getFramework(), context.getNodes(), configuration);
        return AnsibleUtil.getSecretsPath(builder);
    }

    @Override
    public Map<String, String> getRuntimeProperties(ExecutionContext context) {
        return AnsibleUtil.getRuntimeProperties(context, AnsibleDescribable.PROJ_PROP_PREFIX);
    }

    @Override
    public Map<String, String> getRuntimeFrameworkProperties(ExecutionContext context) {
        return AnsibleUtil.getRuntimeProperties(context, AnsibleDescribable.FWK_PROP_PREFIX);
    }

    @Override
    public void setPluginGroup(AnsiblePluginGroup ansiblePluginGroup) {
        this.pluginGroup = ansiblePluginGroup;
    }
}
