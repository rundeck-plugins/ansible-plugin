package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin;
import com.dtolabs.rundeck.plugins.config.ConfiguredBy;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable;
import com.rundeck.plugins.ansible.ansible.AnsibleException;
import com.rundeck.plugins.ansible.ansible.AnsibleRunner;
import com.rundeck.plugins.ansible.ansible.AnsibleRunnerContextBuilder;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.rundeck.plugins.ansible.util.AnsibleUtil;

import java.util.List;
import java.util.Map;

@Plugin(name = AnsiblePlaybookWorflowNodeStep.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
public class AnsiblePlaybookWorflowNodeStep implements NodeStepPlugin, AnsibleDescribable, ProxyRunnerPlugin, ConfiguredBy<AnsiblePluginGroup> {

    public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsiblePlaybookWorflowNodeStep";

    public static Description DESC = null;

    private AnsiblePluginGroup pluginGroup;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.pluginGroup(AnsiblePluginGroup.class);
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible Playbook Workflow Node Step.");
        builder.description("Runs an Ansible Playbook");

        builder.property(BINARIES_DIR_PATH_PROP);
        builder.property(BASE_DIR_PROP);
        builder.property(PLAYBOOK_PATH_PROP);
        builder.property(EXTRA_VARS_PROP);
        builder.property(CONFIG_ENCRYPT_EXTRA_VARS);
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

        builder.mapping(ANSIBLE_BASE_DIR_PATH,PROJ_PROP_PREFIX + ANSIBLE_BASE_DIR_PATH);
        builder.frameworkMapping(ANSIBLE_BASE_DIR_PATH,FWK_PROP_PREFIX + ANSIBLE_BASE_DIR_PATH);

        DESC=builder.build();
    }

    @Override
    public Description getDescription() {
        return DESC;
    }

    @Override
    public void executeNodeStep(
            final PluginStepContext context, final Map<String, Object> configuration, final INodeEntry entry
    ) throws NodeStepException {

        AnsibleRunner runner = null;

        configuration.put(AnsibleDescribable.ANSIBLE_LIMIT,entry.getNodename());
        // set log level
        if (context.getDataContext().get("job").get("loglevel").equals("DEBUG")) {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG,"True");
        } else {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG,"False");
        }

        AnsibleRunnerContextBuilder
                contextBuilder = new AnsibleRunnerContextBuilder(context.getExecutionContext(),
                context.getFramework(),
                context.getNodes(),
                configuration,
                pluginGroup);

        try {
            runner = AnsibleRunner.buildAnsibleRunner(contextBuilder);
            runner.setCustomTmpDirPath(AnsibleUtil.getCustomTmpPathDir(contextBuilder.getFramework()));
        } catch (ConfigurationException e) {
            throw new NodeStepException("Error configuring Ansible runner: "+e.getMessage(), AnsibleException.AnsibleFailureReason.ParseArgumentsError,e.getMessage());
        }

        // ansible runner will take care of handling exceptions, here handle only jobs specific stuff
        try {
            runner.run();
        } catch (AnsibleException e) {
            throw new NodeStepException(e.getMessage(), e.getFailureReason(),e.getMessage());
        } catch (Exception e) {
            throw new NodeStepException(e.getMessage(),AnsibleException.AnsibleFailureReason.AnsibleError,e.getMessage());
        }finally {
            contextBuilder.cleanupTempFiles();
        }
    }

    @Override
    public List<String> listSecretsPathWorkflowNodeStep(ExecutionContext context, INodeEntry node, Map<String, Object> configuration) {
        AnsibleRunnerContextBuilder builder = new AnsibleRunnerContextBuilder(node, context, context.getFramework(), configuration);
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
