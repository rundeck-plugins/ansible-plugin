package com.batix.rundeck.plugins;

import com.batix.rundeck.core.AnsibleDescribable;
import com.batix.rundeck.core.AnsibleException;
import com.batix.rundeck.core.AnsibleRunner;
import com.batix.rundeck.core.AnsibleRunnerBuilder;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import java.util.Map;

@Plugin(name = AnsiblePlaybookInlineNodeStep.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
public class AnsiblePlaybookInlineNodeStep implements NodeStepPlugin, AnsibleDescribable {
	
	public static final String SERVICE_PROVIDER_NAME = "com.batix.rundeck.plugins.AnsiblePlaybookInlineNodeStep";
	
	public static Description DESC = null;
	
    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(SERVICE_PROVIDER_NAME);
        builder.title("Ansible Playbook Inline");
        builder.description("Runs an Inline Ansible Playbook.");

        builder.property(PLAYBOOK_INLINE_PROP);
        builder.property(EXTRA_VARS_PROP);
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

        DESC=builder.build();
    }

  @Override
  public void executeNodeStep(PluginStepContext context, Map<String, Object> configuration, INodeEntry entry) throws NodeStepException {

    AnsibleRunner runner = null;

    // set log level
    if (context.getDataContext().get("job").get("loglevel").equals("DEBUG")) {
        configuration.put(AnsibleDescribable.ANSIBLE_DEBUG,"True");
    } else {
        configuration.put(AnsibleDescribable.ANSIBLE_DEBUG,"False");
    }

    AnsibleRunnerBuilder builder = new AnsibleRunnerBuilder(entry, context.getExecutionContext(), context.getFramework(), configuration);

    try {
        runner = builder.buildAnsibleRunner();
    } catch (ConfigurationException e) {
          throw new NodeStepException("Error configuring Ansible runner: "+e.getMessage(), e, AnsibleException.AnsibleFailureReason.ParseArgumentsError, entry.getNodename());
    }

    // ansible runner will take care of handling exceptions, here handle only jobs specific stuff
    try {
        runner.run();
    } catch (AnsibleException e) {
        throw new NodeStepException(e.getMessage(), e, e.getFailureReason(), entry.getNodename());
    } catch (Exception e) {
        throw new NodeStepException(e.getMessage(), e, AnsibleException.AnsibleFailureReason.AnsibleError, entry.getNodename());
    }

    builder.cleanupTempFiles();
  }

  @Override
  public Description getDescription() {
        return DESC;
  }

}
