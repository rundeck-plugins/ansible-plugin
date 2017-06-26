package com.batix.rundeck.plugins;

import com.batix.rundeck.core.*;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Plugin(name = AnsiblePlaybookNodeStep.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
public class AnsiblePlaybookNodeStep implements NodeStepPlugin, AnsibleDescribable {

    public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsiblePlaybookNodeStep";

    public static Description DESC = null;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible Playbook");
        builder.description("Runs an Ansible Playbook.");

        builder.property(ANSIBLE_INLINE_TASKS_PROP);
        builder.property(VAULT_KEY_FILE_PROP);
        builder.property(VAULT_KEY_STORAGE_PROP);
        builder.property(EXTRA_ATTRS_PROP);
        builder.property(SSH_AUTH_TYPE_PROP);
        builder.property(SSH_USER_PROP);
        builder.property(SSH_PASSWORD_STORAGE_PROP);
        builder.property(SSH_KEY_FILE_PROP);
        builder.property(SSH_KEY_STORAGE_PROP);
        builder.property(SSH_TIMEOUT_PROP);
        builder.property(BECOME_PROP);
        builder.property(BECOME_AUTH_TYPE_PROP);
        builder.property(BECOME_USER_PROP);
        builder.property(BECOME_PASSWORD_STORAGE_PROP);

        DESC = builder.build();
    }

    @Override
    public void executeNodeStep(PluginStepContext context, Map<String, Object> configuration, INodeEntry entry) throws NodeStepException {

        AnsibleRunner runner = null;


        String vars = new AnsibleVarsBuilder(context.getDataContext()).buildVars();
        String tasks = (String) configuration.get(AnsibleDescribable.ANSIBLE_INLINE_TASKS);
        File playbook = null;
        try {
            playbook = AnsiblePlaybookBuilder.withTasks(tasks);
        } catch (ConfigurationException e) {
            throw new NodeStepException(e.getMessage(), e, AnsibleException.AnsibleFailureReason.AnsibleError,  entry.getNodename());
        }

        configuration.put(AnsibleDescribable.ANSIBLE_PLAYBOOK, playbook.getAbsolutePath());
        configuration.put(AnsibleDescribable.ANSIBLE_EXTRA_VARS, vars);

        // set log level
        if (context.getDataContext().get("job").get("loglevel").equals("DEBUG")) {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG, "True");
        } else {
            configuration.put(AnsibleDescribable.ANSIBLE_DEBUG, "False");
        }

        AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(entry, context.getExecutionContext(), context.getFramework(), configuration);

        try {
            runner = builder.buildAnsibleRunner();
        } catch (ConfigurationException e) {
            throw new NodeStepException("Error configuring Ansible runner.", e, AnsibleException.AnsibleFailureReason.ParseArgumentsError, entry.getNodename());
        }

        // ansible runner will take care of handling exceptions, here handle only jobs specific stuff
        try {
            runner.run();
        } catch (AnsibleException e) {
            throw new NodeStepException(e.getMessage(), e, e.getFailureReason(), entry.getNodename());
        } catch (Exception e) {
            throw new NodeStepException(e.getMessage(), e, AnsibleException.AnsibleFailureReason.AnsibleError,  entry.getNodename());
        }

        playbook.delete();
        builder.cleanupTempFiles();
    }



    @Override
    public Description getDescription() {
        return DESC;
    }

}
