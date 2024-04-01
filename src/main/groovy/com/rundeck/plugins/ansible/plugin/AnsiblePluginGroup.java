package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.PluginAdapterUtility;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.config.PluginGroup;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;


@Plugin(service = ServiceNameConstants.PluginGroup, name = "AnsiblePluginGroup")
@PluginDescription(title = "Ansible Project Configuration", description = "Plugin basic ansible configurations")
public class AnsiblePluginGroup implements PluginGroup, Describable {

    public String getAnsibleConfigFilePath() {
        return ansibleConfigFilePath;
    }

    public void setAnsibleConfigFilePath(String ansibleConfigFilePath) {
        this.ansibleConfigFilePath = ansibleConfigFilePath;
    }

    public String getAnsibleBinariesDirPath() {
        return ansibleBinariesDirPath;
    }

    public void setAnsibleBinariesDirPath(String ansibleBinariesDirPath) {
        this.ansibleBinariesDirPath = ansibleBinariesDirPath;
    }

    public Boolean getEncryptExtraVars() {
        return encryptExtraVars;
    }

    public void setEncryptExtraVars(Boolean encryptExtraVars) {
        this.encryptExtraVars = encryptExtraVars;
    }

    @PluginProperty(
            title = "Ansible config file path",
            description = "Set ansible config file path."
    )
    String ansibleConfigFilePath;

    @PluginProperty(
            title = "Ansible binaries directory path",
            description = "Set ansible binaries directory path."
    )
    String ansibleBinariesDirPath;

    @PluginProperty(
            title = "Encrypt Extra Vars.",
            description = "Encrypt the value of the extra vars keys."
    )
    Boolean encryptExtraVars;

    @Override
    public Description getDescription() {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        Description description = PluginAdapterUtility.buildDescription(this, builder);
        return builder.build();
    }
}
