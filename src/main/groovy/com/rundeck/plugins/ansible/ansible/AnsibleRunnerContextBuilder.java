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
import lombok.extern.slf4j.Slf4j;
import org.rundeck.storage.api.Path;

import java.util.*;

import org.rundeck.storage.api.PathUtil;
import org.rundeck.storage.api.StorageException;

@Slf4j
@Getter
public class AnsibleRunnerContextBuilder {

    private final ExecutionContext context;
    private final Framework framework;
    private final String frameworkProject;
    private final Map<String, Object> jobConf;
    private final Collection<INodeEntry> nodes;
    private final Collection<File> tempFiles;
    private File executionSpecificDir;

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
        return getPrivateKeyStoragePath(getNode());
    }

    public String getPrivateKeyStoragePath(INodeEntry node) {
        return resolveAndExpandStoragePath(
                AnsibleDescribable.ANSIBLE_SSH_KEYPATH_STORAGE_PATH,
                node
        );
    }

    public byte[] getPrivateKeyStorageDataBytes() throws IOException {
        String privateKeyResourcePath = getPrivateKeyStoragePath();
        return this.loadStoragePathData(privateKeyResourcePath);
    }

    public String getPasswordStoragePath() {
        return getPasswordStoragePath(getNode());
    }

    public String getPasswordStoragePath(INodeEntry node) {
        return resolveAndExpandStoragePath(
                AnsibleDescribable.ANSIBLE_SSH_PASSWORD_STORAGE_PATH,
                node
        );
    }

    /**
     * Helper method to resolve a storage path property and expand any data references.
     * Extracted to reduce duplication between password and private key storage path resolution.
     */
    private String resolveAndExpandStoragePath(String propertyName, INodeEntry node) {
        String path = PropertyResolver.resolveProperty(
                propertyName,
                null,
                getFrameworkProject(),
                getFramework(),
                node,
                getJobConf()
        );

        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public String getSshPrivateKey() throws ConfigurationException {
        return getSshPrivateKey(getNode());
    }

    public String getSshPrivateKey(INodeEntry node) throws ConfigurationException {
        //look for storage option
        String storagePath = getPrivateKeyStoragePath(node);

        if (null != storagePath) {
            return readFromStoragePath(storagePath, "ssh private key");
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
        return getSshPassword(getNode());
    }

    public String getSshPassword(INodeEntry node) throws ConfigurationException {

        //look for option values first
        //typically jobs use secure options to dynamically setup the ssh password
        final String passwordOption = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_PASSWORD_OPTION,
                AnsibleDescribable.DEFAULT_ANSIBLE_SSH_PASSWORD_OPTION,
                getFrameworkProject(),
                getFramework(),
                node,
                getJobConf()
        );
        String sshPassword = PropertyResolver.evaluateSecureOption(passwordOption, getContext());

        if (null != sshPassword) {
            // is true if there is an ssh option defined in the private data context
            return sshPassword;
        } else {
            //look for storage option
            String storagePath = getPasswordStoragePath(node);

            if (null != storagePath) {
                //look up storage value
                return getPasswordFromPath(storagePath);

            } else {
                return null;
            }
        }
    }

    public String getPasswordFromPath(String storagePath) throws ConfigurationException {
        return readFromStoragePath(storagePath, "ssh password");
    }

    /**
     * Helper method to read content from Rundeck storage path.
     * Extracted to reduce duplication between password and private key reading.
     *
     * @param storagePath The storage path to read from
     * @param resourceType Description of resource type for error messages (e.g., "ssh password", "ssh private key")
     * @return The content as a UTF-8 string, or null if storagePath is null
     * @throws ConfigurationException if reading fails
     */
    private String readFromStoragePath(String storagePath, String resourceType) throws ConfigurationException {
        if (storagePath == null) {
            return null;
        }

        Path path = PathUtil.asPath(storagePath);
        try {
            ResourceMeta contents = context.getStorageTree().getResource(path)
                    .getContents();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            contents.writeContent(byteArrayOutputStream);
            return byteArrayOutputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (StorageException | IOException e) {
            throw new ConfigurationException("Failed to read the " + resourceType + " for " +
                    "storage path: " + storagePath + ": " + e.getMessage());
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

    public String getSshNodeUser(INodeEntry node) {
        final String user;
        user = node.getUsername();
        if (null != user && user.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(user, getContext().getDataContext());
        }
        return user;
    }


    public AuthenticationType getSshAuthenticationType() {
        return getSshAuthenticationType(getNode());
    }


    public AuthenticationType getSshAuthenticationType(INodeEntry node) {
        String authType = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_AUTH_TYPE,
                null,
                getFrameworkProject(),
                getFramework(),
                node,
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
            String executionSpecificDir = getExecutionSpecificTmpDir();
            File tempInventory = new AnsibleInventoryBuilder(this.nodes, executionSpecificDir).buildInventory();
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
            String executionSpecificDir = getExecutionSpecificTmpDir();
            File tempInventory = new AnsibleInlineInventoryBuilder(inline_inventory, executionSpecificDir).buildInventory();
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
        String baseDir = null;
        if (getJobConf().containsKey(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH)) {
            baseDir = (String) jobConf.get(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH);
        }

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
        // Clean up individual temp files
        for (File temp : tempFiles) {
            if (!getDebug()) {
                log.debug("Attempting to delete temp file: {}", temp.getAbsolutePath());
                if (!temp.delete()) {
                    log.warn("Failed to delete temp file: {}. Marking for deletion on JVM exit.", temp.getAbsolutePath());
                    // Fallback: schedule for deletion on JVM exit
                    temp.deleteOnExit();
                } else {
                    log.debug("Successfully deleted temp file: {}", temp.getAbsolutePath());
                }
            }
        }
        tempFiles.clear();

        // Clean up execution-specific directory (including group_vars)
        if (!getDebug() && executionSpecificDir != null && executionSpecificDir.exists()) {
            log.debug("Cleaning up execution-specific directory: {}", executionSpecificDir.getAbsolutePath());
            if (!deleteDirectoryRecursively(executionSpecificDir)) {
                log.warn("Failed to completely delete execution-specific directory: {}", executionSpecificDir.getAbsolutePath());
            } else {
                log.debug("Successfully deleted execution-specific directory: {}", executionSpecificDir.getAbsolutePath());
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory The directory to delete
     * @return true if the directory and all its contents were successfully deleted, false otherwise
     */
    private boolean deleteDirectoryRecursively(File directory) {
        if (directory == null || !directory.exists()) {
            return true;
        }

        boolean success = true;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!deleteDirectoryRecursively(file)) {
                        success = false;
                        log.warn("Failed to delete subdirectory: {}", file.getAbsolutePath());
                    }
                } else {
                    if (!file.delete()) {
                        success = false;
                        log.warn("Failed to delete file: {}", file.getAbsolutePath());
                    }
                }
            }
        }

        if (!directory.delete()) {
            success = false;
            log.warn("Failed to delete directory: {}", directory.getAbsolutePath());
        }

        return success;
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

    /*
    Returns a map of node names to their respective authentication details.
    Each entry in the outer map corresponds to a node, with the key being the node name
    and the value being another map containing authentication parameters such as

    "ansible_ssh_private_key" or "ansible_password".
    [ "node1": { "ansible_ssh_private_key": "...", "ansible_user": "..." },
      "node2": { "ansible_password": "...", "ansible_user": "..." }
      ]

     */
    public Map<String, Map<String, String>> getNodesAuthenticationMap(){

        Map<String, Map<String, String>> authenticationNodesMap = new HashMap<>();

        this.context.getNodes().forEach((node) -> {
            Map<String, String> auth = new HashMap<>();
            final AuthenticationType authType = getSshAuthenticationType(node);

            if (getDebug()) {
                System.err.println("DEBUG: Processing authentication for node '" + node.getNodename() +
                        "' with auth type: " + authType);
            }

            if (AnsibleDescribable.AuthenticationType.privateKey == authType) {
                final String privateKey;
                try {
                    privateKey = getSshPrivateKey(node);
                    if (getDebug()) {
                        System.err.println("DEBUG: Retrieved private key for node '" +
                                node.getNodename() + "': " + (privateKey != null ? ("yes, length=" + privateKey.length()) : "null"));
                    }
                } catch (ConfigurationException e) {
                    if (getDebug()) {
                        System.err.println("DEBUG: Error retrieving private key for node '" +
                                node.getNodename() + "': " + e.getMessage());
                    }
                    throw new RuntimeException("Failed to retrieve private key for node '" +
                            node.getNodename() + "': " + e.getMessage(), e);
                }
                if (privateKey != null) {
                    auth.put("ansible_ssh_private_key", privateKey);
                    if (getDebug()) {
                        System.err.println("DEBUG: Added private key to auth map for node '" + node.getNodename() + "'");
                    }
                } else {
                    if (getDebug()) {
                        System.err.println("DEBUG: Private key is null for node '" + node.getNodename() + "', not adding to auth map");
                    }
                }
            } else if (AnsibleDescribable.AuthenticationType.password == authType) {
                try {
                    String password = getSshPassword(node);
                    if(null!=password){
                        auth.put("ansible_password", password);
                        if (getDebug()) {
                            System.err.println("DEBUG: Successfully retrieved password for node '" +
                                    node.getNodename() + "'");
                        }
                    }
                } catch (ConfigurationException e) {
                    if (getDebug()) {
                        System.err.println("DEBUG: Error retrieving password for node '" +
                                node.getNodename() + "': " + e.getMessage());
                    }
                    throw new RuntimeException("Failed to retrieve password for node '" +
                            node.getNodename() + "': " + e.getMessage(), e);
                }
            }

            String userName = getSshNodeUser(node);

            if(null!=userName){
                auth.put("ansible_user",userName );
            }

            // Validate that node has at least one authentication method configured
            boolean hasPassword = auth.containsKey("ansible_password");
            boolean hasPrivateKey = auth.containsKey("ansible_ssh_private_key");

            if (!hasPassword && !hasPrivateKey) {
                context.getExecutionLogger().log(2, "WARNING: Node '" + node.getNodename() +
                        "' has no password or private key configured. Authentication may fail.");
                if (getDebug()) {
                    System.err.println("DEBUG: Node '" + node.getNodename() +
                            "' has no credentials configured (only username: " + (auth.containsKey("ansible_user") ? "yes" : "no") + ")");
                }
            }

            authenticationNodesMap.put(node.getNodename(), auth);
        });

        return authenticationNodesMap;
    }


    public List<String> getListNodesKeyPath(){

        if(!generateInventoryNodesAuth()) {
            return new ArrayList<>();
        }

        List<String> secretPaths = new ArrayList<>();

        this.context.getNodes().forEach((node) -> {
            String keyPath = PropertyResolver.resolveProperty(
                    AnsibleDescribable.ANSIBLE_SSH_PASSWORD_STORAGE_PATH,
                    null,
                    getFrameworkProject(),
                    getFramework(),
                    node,
                    getJobConf()
            );

            if(null!=keyPath){
                if(!secretPaths.contains(keyPath)){
                    secretPaths.add(keyPath);
                }
            }

            String privateKeyPath = PropertyResolver.resolveProperty(
                    AnsibleDescribable.ANSIBLE_SSH_KEYPATH_STORAGE_PATH,
                    null,
                    getFrameworkProject(),
                    getFramework(),
                    node,
                    getJobConf()
            );

            if(null!=privateKeyPath){
                if(!secretPaths.contains(privateKeyPath)){
                    secretPaths.add(privateKeyPath);
                }
            }
        });

        return secretPaths;
    }


    public Boolean generateInventoryNodesAuth() {
        Boolean generateInventoryNodesAuth = null;

        if(getDebug()) {
            System.err.println("DEBUG: Resolving property ANSIBLE_GENERATE_INVENTORY_NODES_AUTH");
            System.err.println("DEBUG: Property key: " + AnsibleDescribable.ANSIBLE_GENERATE_INVENTORY_NODES_AUTH);
            System.err.println("DEBUG: Framework project: " + getFrameworkProject());
        }

        String sgenerateInventoryNodesAuth = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_GENERATE_INVENTORY_NODES_AUTH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getJobConf()
        );

        if(getDebug()) {
            System.err.println("DEBUG: PropertyResolver returned: " + sgenerateInventoryNodesAuth);
        }

        if (null != sgenerateInventoryNodesAuth) {
            generateInventoryNodesAuth = Boolean.parseBoolean(sgenerateInventoryNodesAuth);
            if(getDebug()) {
                System.err.println("DEBUG: Parsed to boolean: " + generateInventoryNodesAuth);
            }
        } else {
            if(getDebug()) {
                System.err.println("DEBUG: Property not found, returning null");
            }
        }

        return generateInventoryNodesAuth;
    }

    /**
     * Creates and returns an execution-specific temporary directory path.
     * This ensures that each execution has its own isolated directory for inventory and group_vars,
     * preventing conflicts when multiple workflow step executions run in parallel.
     *
     * @return The path to the execution-specific directory
     */
    String getExecutionSpecificTmpDir() {
        // Return cached directory if already created
        if (executionSpecificDir != null) {
            if (getDebug()) {
                System.err.println("DEBUG: Using cached execution-specific directory: " + executionSpecificDir.getAbsolutePath());
            }
            return executionSpecificDir.getAbsolutePath();
        }

        String executionId = null;

        // Get execution ID from data context
        if (context.getDataContext() != null && context.getDataContext().get("job") != null) {
            executionId = context.getDataContext().get("job").get("execid");

            if (getDebug()) {
                System.err.println("DEBUG: Execution ID from context: " + executionId);
            }
        }

        // Get base tmp directory
        String baseTmpDir = AnsibleUtil.getCustomTmpPathDir(framework);

        // Create execution-specific directory
        if (executionId != null && !executionId.isEmpty()) {
            executionSpecificDir = new File(baseTmpDir, "ansible-exec-" + executionId);
            if (!executionSpecificDir.exists()) {
                log.debug("Creating execution-specific directory: {}", executionSpecificDir.getAbsolutePath());
                // Check return value to handle race conditions and creation failures
                boolean created = executionSpecificDir.mkdirs();
                if (!created && !executionSpecificDir.exists()) {
                    // Failed to create and directory still doesn't exist - this is an error
                    String errorMsg = "Failed to create execution-specific directory: " + executionSpecificDir.getAbsolutePath();
                    log.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
                log.debug("Successfully created execution-specific directory: {}", executionSpecificDir.getAbsolutePath());
            } else {
                log.debug("Execution-specific directory already exists: {}", executionSpecificDir.getAbsolutePath());
            }
            return executionSpecificDir.getAbsolutePath();
        }
        return baseTmpDir;
    }
}
