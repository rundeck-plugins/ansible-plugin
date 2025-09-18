/**
 * Ansible Runner Builder.
 *
 * @author Yassine Azzouz <a href="mailto:yassine.azzouz@gmail.com">yassine.azzouz@gmail.com</a>
 */
package com.rundeck.plugins.ansible.ansible;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable.AuthenticationType;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable.BecomeMethodType;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.storage.ResourceMeta;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.rundeck.plugins.ansible.plugin.AnsiblePluginGroup;
import com.rundeck.plugins.ansible.util.AnsibleUtil;
import lombok.Getter;
import org.rundeck.storage.api.Path;

import java.util.*;

import org.rundeck.storage.api.PathUtil;
import org.rundeck.storage.api.StorageException;

@Getter
public class AnsibleRunnerContextBuilder {

    private final ExecutionContext context;
    private final Framework framework;
    private final String frameworkProject;
    private final Map<String, Object> jobConf;
    private final Collection<INodeEntry> nodes;
    private final Collection<File> tempFiles;

    private AnsiblePluginGroup pluginGroup;


    public AnsibleRunnerContextBuilder(final ExecutionContext context, final Framework framework, INodeSet nodes, final Map<String, Object> configuration) {
        this.context = context;
        this.framework = framework;
        this.frameworkProject = context.getFrameworkProject();
        this.jobConf = configuration;
        this.nodes = nodes.getNodes();
        this.tempFiles = new LinkedList<>();
    }

    public AnsibleRunnerContextBuilder(final INodeEntry node, final ExecutionContext context, final Framework framework, final Map<String, Object> configuration) {
        this.context = context;
        this.framework = framework;
        this.frameworkProject = context.getFrameworkProject();
        this.jobConf = configuration;
        this.nodes = Collections.singleton(node);
        this.tempFiles = new LinkedList<>();
    }

    public AnsibleRunnerContextBuilder(final ExecutionContext context, final Framework framework, INodeSet nodes, final Map<String, Object> configuration, final AnsiblePluginGroup pluginGroup) {
        this.context = context;
        this.framework = framework;
        this.frameworkProject = context.getFrameworkProject();
        this.jobConf = configuration;
        this.nodes = nodes.getNodes();
        this.tempFiles = new LinkedList<>();
        this.pluginGroup = pluginGroup;
    }

    private byte[] loadStoragePathData(final String passwordStoragePath) throws IOException {
        if (null == passwordStoragePath) {
            return null;
        }
        ResourceMeta contents = context.getStorageTree().getResource(passwordStoragePath).getContents();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        contents.writeContent(byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public String getPrivateKeyfilePath() {
        String path = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_KEYPATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public String getPrivateKeyStoragePath() {
        String path = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_KEYPATH_STORAGE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );
        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public byte[] getPrivateKeyStorageDataBytes() throws IOException {
        String privateKeyResourcePath = getPrivateKeyStoragePath();
        return this.loadStoragePathData(privateKeyResourcePath);
    }

    public String getPasswordStoragePath() {

        String path = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_PASSWORD_STORAGE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public String getSshPrivateKey() throws ConfigurationException {
        //look for storage option
        String storagePath = getPrivateKeyStoragePath();

        if (null != storagePath) {
            Path path = PathUtil.asPath(storagePath);
            try {
                ResourceMeta contents = context.getStorageTree().getResource(path)
                        .getContents();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                contents.writeContent(byteArrayOutputStream);
                return byteArrayOutputStream.toString();
            } catch (StorageException | IOException e) {
                throw new ConfigurationException("Failed to read the ssh private key for " +
                        "storage path: " + storagePath + ": " + e.getMessage());
            }
        } else {
            //else look up option value
            final String path = getPrivateKeyfilePath();
            if (path != null) {
                try {
                    return new String(Files.readAllBytes(Paths.get(path)));
                } catch (IOException e) {
                    throw new ConfigurationException("Failed to read the ssh private key from path " +
                            path + ": " + e.getMessage());
                }
            } else {
                return null;
            }
        }
    }

    public String getSshPassword() throws ConfigurationException {

        //look for option values first
        //typically jobs use secure options to dynamically setup the ssh password
        final String passwordOption = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_PASSWORD_OPTION,
                AnsibleDescribable.DEFAULT_ANSIBLE_SSH_PASSWORD_OPTION,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );
        String sshPassword = PropertyResolver.evaluateSecureOption(passwordOption, getContext());

        if (null != sshPassword) {
            // is true if there is an ssh option defined in the private data context
            return sshPassword;
        } else {
            //look for storage option
            String storagePath = getPasswordStoragePath();

            if (null != storagePath) {
                //look up storage value
                Path path = PathUtil.asPath(storagePath);
                try {
                    ResourceMeta contents = context.getStorageTree().getResource(path)
                            .getContents();
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    contents.writeContent(byteArrayOutputStream);
                    return byteArrayOutputStream.toString();
                } catch (StorageException | IOException e) {
                    throw new ConfigurationException("Failed to read the ssh password for " +
                            "storage path: " + storagePath + ": " + e.getMessage());
                }

            } else {
                return null;
            }
        }
    }

    public Integer getSSHTimeout() throws ConfigurationException {
        Integer timeout = null;
        final String stimeout = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_TIMEOUT,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );
        if (null != stimeout) {
            try {
                timeout = Integer.parseInt(stimeout);
            } catch (NumberFormatException e) {
                throw new ConfigurationException("Can't parse timeout value" +
                        timeout + ": " + e.getMessage());
            }
        }
        return timeout;
    }

    public String getSshUser() {
        final String user;
        user = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_USER,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != user && user.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(user, getContext().getDataContext());
        }
        return user;
    }


    public AuthenticationType getSshAuthenticationType() {
        String authType = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_AUTH_TYPE,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != authType) {
            return AuthenticationType.valueOf(authType);
        }
        return AuthenticationType.privateKey;
    }

    public String getBecomeUser() {
        final String user;
        user = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BECOME_USER,
                null,
                getFrameworkProject(),
                getFramework(), getNode(),
                getJobConf()
        );

        if (null != user && user.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(user, getContext().getDataContext());
        }
        return user;
    }

    public Boolean getBecome() {
        Boolean become = null;
        String sbecome = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BECOME,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != sbecome) {
            become = Boolean.parseBoolean(sbecome);
        }
        return become;
    }

    public String getExtraParams() {
        final String extraParams;
        extraParams = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_EXTRA_PARAM,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != extraParams && extraParams.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(extraParams, getContext().getDataContext());
        }
        return extraParams;
    }

    public BecomeMethodType getBecomeMethod() {
        String becomeMethod = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BECOME_METHOD,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != becomeMethod) {
            return BecomeMethodType.valueOf(becomeMethod);
        }
        return null;
    }

    public byte[] getPasswordStorageData() throws IOException {
        return loadStoragePathData(getPasswordStoragePath());
    }

    public String getBecomePasswordStoragePath() {
        String path = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_STORAGE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );
        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public byte[] getBecomePasswordStorageData() throws IOException {
        return loadStoragePathData(getBecomePasswordStoragePath());
    }

    public String getBecomePassword() throws ConfigurationException {

        //look for option values first
        //typically jobs use secure options to dynamically setup the become password
        String passwordOption = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_OPTION,
                AnsibleDescribable.DEFAULT_ANSIBLE_BECOME_PASSWORD_OPTION,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );
        String becomePassword = PropertyResolver.evaluateSecureOption(passwordOption, getContext());

        if (null != becomePassword) {
            // is true if there is a become option defined in the private data context
            return becomePassword;
        } else {
            //look for storage option
            String storagePath = PropertyResolver.resolveProperty(
                    AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_STORAGE_PATH,
                    null,
                    getFrameworkProject(),
                    getFramework(),
                    getNode(),
                    getJobConf()
            );

            if (null != storagePath) {
                //look up storage value
                if (storagePath.contains("${")) {
                    storagePath = DataContextUtils.replaceDataReferencesInString(
                            storagePath,
                            context.getDataContext()
                    );
                }
                Path path = PathUtil.asPath(storagePath);
                try {
                    ResourceMeta contents = context.getStorageTree().getResource(path)
                            .getContents();
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    contents.writeContent(byteArrayOutputStream);
                    return byteArrayOutputStream.toString();
                } catch (StorageException | IOException e) {
                    throw new ConfigurationException("Failed to read the become password for " +
                            "storage path: " + storagePath + ": " + e.getMessage());
                }

            } else {
                return null;
            }
        }
    }

    public String  getVaultKeyStoragePath(){

        String storagePath = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_VAULTSTORE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if(null!=storagePath) {
            //expand properties in path
            if (storagePath.contains("${")) {
                storagePath = DataContextUtils.replaceDataReferencesInString(
                        storagePath,
                        context.getDataContext()
                );
            }

            return storagePath;
        }

        return null;

    }

    public String getVaultKey()  throws ConfigurationException{
        //look for storage option
        String storagePath = getVaultKeyStoragePath();

        if(null!=storagePath){
            //look up storage value
            Path path = PathUtil.asPath(storagePath);
            try {
                ResourceMeta contents = context.getStorageTree().getResource(path)
                        .getContents();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                contents.writeContent(byteArrayOutputStream);
                return byteArrayOutputStream.toString();
            } catch (StorageException | IOException e) {
                throw new ConfigurationException("Failed to read the vault key for " +
                        "storage path: " + storagePath + ": " + e.getMessage());
            }
        } else {

            String path = PropertyResolver.resolveProperty(
                    AnsibleDescribable.ANSIBLE_VAULT_PATH,
                    null,
                    getFrameworkProject(),
                    getFramework(),
                    getNode(),
                    getJobConf()
            );

            //expand properties in path
            if (path != null && path.contains("${")) {
                path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
            }

            if (path != null) {
                try {
                    return new String(Files.readAllBytes(Paths.get(path)));
                } catch (IOException e) {
                    throw new ConfigurationException("Failed to read the ssh private key from path " +
                            path + ": " + e.getMessage());
                }
            } else {
                return null;
            }
        }
    }

    public String getPlaybookPath() {
        String playbook = null;
        if (getJobConf().containsKey(AnsibleDescribable.ANSIBLE_PLAYBOOK_PATH)) {
            playbook = (String) jobConf.get(AnsibleDescribable.ANSIBLE_PLAYBOOK_PATH);
        }

        if (null != playbook && playbook.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(playbook, getContext().getDataContext());
        }
        return playbook;
    }

    public String getPlaybookInline() {
        String playbook = null;
        if (getJobConf().containsKey(AnsibleDescribable.ANSIBLE_PLAYBOOK_INLINE)) {
            playbook = (String) jobConf.get(AnsibleDescribable.ANSIBLE_PLAYBOOK_INLINE);
        }

        if (null != playbook && playbook.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(playbook, getContext().getDataContext());
        }
        return playbook;
    }

    public String getModule() {
        String module = null;
        if (getJobConf().containsKey(AnsibleDescribable.ANSIBLE_MODULE)) {
            module = (String) jobConf.get(AnsibleDescribable.ANSIBLE_MODULE);
        }

        if (null != module && module.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(module, getContext().getDataContext());
        }
        return module;
    }

    public String getModuleArgs() {
        String args = null;
        if (getJobConf().containsKey(AnsibleDescribable.ANSIBLE_MODULE_ARGS)) {
            args = (String) jobConf.get(AnsibleDescribable.ANSIBLE_MODULE_ARGS);
        }

        if (null != args && args.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(args, getContext().getDataContext());
        }
        return args;
    }

    public String getExecutable() {
        final String executable;
        executable = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_EXECUTABLE,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != executable && executable.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(executable, getContext().getDataContext());
        }
        return executable;
    }

    public Boolean getDebug() {
        Boolean debug = Boolean.FALSE;
        String sdebug = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_DEBUG,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != sdebug) {
            debug = Boolean.parseBoolean(sdebug);
        }
        return debug;
    }

    public String getExtraVars() {
        final String extraVars;
        extraVars = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_EXTRA_VARS,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != extraVars && extraVars.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(extraVars, getContext().getDataContext());
        }
        return extraVars;
    }

    public Boolean generateInventory() {
        Boolean generateInventory = null;
        String sgenerateInventory = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_GENERATE_INVENTORY,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != sgenerateInventory) {
            generateInventory = Boolean.parseBoolean(sgenerateInventory);
        }
        return generateInventory;
    }

    public String getInventory() throws ConfigurationException {
        String inventory;
        String inline_inventory;
        Boolean isGenerated = generateInventory();


        if (isGenerated != null && isGenerated) {
            File tempInventory = new AnsibleInventoryBuilder(this.nodes, AnsibleUtil.getCustomTmpPathDir(framework)).buildInventory();
            tempFiles.add(tempInventory);
            inventory = tempInventory.getAbsolutePath();
            return inventory;
        }
        inline_inventory = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_INVENTORY_INLINE,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (inline_inventory != null) {
            /* Create tmp file with inventory */
            /*
            the builder gets the nodes from rundeck in rundeck node format and converts to ansible inventory
            we don't want that, we simply want the list we provided in ansible format
             */
            File tempInventory = new AnsibleInlineInventoryBuilder(inline_inventory,AnsibleUtil.getCustomTmpPathDir(framework)).buildInventory();
            tempFiles.add(tempInventory);
            inventory = tempInventory.getAbsolutePath();
            return inventory;
        }

        inventory = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_INVENTORY,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != inventory && inventory.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(inventory, getContext().getDataContext());
        }

        return inventory;
    }

    public String getLimit() throws ConfigurationException {
        final String limit;

        // Return Null if Disabled
        if (PropertyResolver.resolveBooleanProperty(
                AnsibleDescribable.ANSIBLE_DISABLE_LIMIT,
                Boolean.valueOf(AnsibleDescribable.DISABLE_LIMIT_PROP.getDefaultValue()),
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf())) {

            return null;
        }

        // Get Limit from Rundeck
        limit = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_LIMIT,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != limit && limit.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(limit, getContext().getDataContext());
        }
        return limit;
    }

    public String getConfigFile() {

        String configFile;
        configFile = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_CONFIG_FILE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != configFile && configFile.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(configFile, getContext().getDataContext());
        }

        if(null == configFile || configFile.isEmpty()) {
            if (this.pluginGroup != null && this.pluginGroup.getAnsibleConfigFilePath() != null && !this.pluginGroup.getAnsibleConfigFilePath().isEmpty()) {
                this.context.getExecutionLogger().log(
                        4, "plugin group set getAnsibleConfigFilePath: " + this.pluginGroup.getAnsibleConfigFilePath()
                );

                configFile = this.pluginGroup.getAnsibleConfigFilePath();
            }
        }


        return configFile;
    }

    public String getBaseDir() {
        String baseDir;
        baseDir = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BASE_DIR_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != baseDir && baseDir.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(baseDir, getContext().getDataContext());
        }
        return baseDir;
    }

    public String getBinariesFilePath() {
        String binariesFilePathStr;
        binariesFilePathStr = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != binariesFilePathStr && binariesFilePathStr.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(binariesFilePathStr, getContext().getDataContext());
        }

        if(null == binariesFilePathStr || binariesFilePathStr.isEmpty()){
            if(this.pluginGroup!=null && this.pluginGroup.getAnsibleBinariesDirPath()!= null &&  !this.pluginGroup.getAnsibleBinariesDirPath().isEmpty()){
                this.context.getExecutionLogger().log(
                        4, "plugin group set getAnsibleBinariesDirPath: "+this.pluginGroup.getAnsibleBinariesDirPath()
                );
                binariesFilePathStr =  this.pluginGroup.getAnsibleBinariesDirPath();
            }
        }


        return binariesFilePathStr;
    }

    public INodeEntry getNode() {
        return nodes.size() == 1 ? nodes.iterator().next() : null;
    }


    public void cleanupTempFiles() {
        for (File temp : tempFiles) {
            if (!getDebug()) {
                temp.delete();
            }
        }
        tempFiles.clear();
    }

    public Boolean getUseSshAgent() {
        boolean useAgent = false;
        String sAgent = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_USE_AGENT,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != sAgent) {
            useAgent = Boolean.parseBoolean(sAgent);
        }
        return useAgent;
    }

    String getPassphrase() throws ConfigurationException {
        //look for option values first
        //typically jobs use secure options to dynamically setup the ssh password
        final String passphraseOption = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_PASSPHRASE_OPTION,
                AnsibleDescribable.DEFAULT_ANSIBLE_SSH_PASSPHRASE_OPTION,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );
        String sshPassword = PropertyResolver.evaluateSecureOption(passphraseOption, getContext());

        if (null != sshPassword) {
            // is true if there is an ssh option defined in the private data context
            return sshPassword;
        } else {
            return getPassphraseStorageData(getPassphraseStoragePath());
        }
    }

    public String getPassphraseStoragePath() {

        String storagePath = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_PASSPHRASE,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if (null != storagePath) {
            //expand properties in path
            if (storagePath.contains("${")) {
                storagePath = DataContextUtils.replaceDataReferencesInString(storagePath, context.getDataContext());
            }

            return storagePath;
        }

        return null;

    }

    public String getPassphraseStorageData(String storagePath) throws ConfigurationException {
        if (storagePath == null) {
            return null;
        }

        Path path = PathUtil.asPath(storagePath);
        try {
            ResourceMeta contents = context.getStorageTree().getResource(path)
                    .getContents();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            contents.writeContent(byteArrayOutputStream);
            return byteArrayOutputStream.toString();
        } catch (StorageException | IOException e) {
            throw new ConfigurationException("Failed to read the ssh Passphrase for " +
                    "storage path: " + storagePath + ": " + e.getMessage());
        }
    }

    public boolean encryptExtraVars() throws ConfigurationException {
        boolean encryptExtraVars =  PropertyResolver.resolveBooleanProperty(
                AnsibleDescribable.ANSIBLE_ENCRYPT_EXTRA_VARS,
                false,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if(!encryptExtraVars){
            if(this.pluginGroup!=null && this.pluginGroup.getEncryptExtraVars()!= null &&  this.pluginGroup.getEncryptExtraVars()){
                this.context.getExecutionLogger().log(
                        4, "plugin group set getEncryptExtraVars: "+this.pluginGroup.getEncryptExtraVars()
                );
                encryptExtraVars =  this.pluginGroup.getEncryptExtraVars();
            }
        }

        return encryptExtraVars;
    }

    public Map<String,String> getListOptions(){
        Map<String, String> options = new HashMap<>();
        Map<String, String> optionsContext = context.getDataContext().get("option");
        Map<String, String> secureOptionContext = context.getDataContext().get("secureOption");
        if (optionsContext != null && secureOptionContext!=null) {
            optionsContext.forEach((option, value) -> {
                if(!secureOptionContext.containsKey(option)){
                    options.put(option, value);
                }
            });
        }else if (optionsContext != null) {
            options.putAll(optionsContext);
        }
        return options;
    }
}
