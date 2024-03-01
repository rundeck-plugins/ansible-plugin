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

import org.rundeck.storage.api.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

import org.rundeck.storage.api.PathUtil;
import org.rundeck.storage.api.StorageException;

public class AnsibleRunnerBuilder {

    private ExecutionContext context;
    private Framework framework;
    private String frameworkProject;
    private Map<String, Object> jobConf;
    private Collection<INodeEntry> nodes;
    private Collection<File> tempFiles;


    public AnsibleRunnerBuilder(final ExecutionContext context, final Framework framework, INodeSet nodes, final Map<String, Object> configuration) {
        this.context = context;
        this.framework = framework;
        this.frameworkProject = context.getFrameworkProject();
        this.jobConf = configuration;
        this.nodes = nodes.getNodes();
        this.tempFiles = new LinkedList<>();
    }

    public AnsibleRunnerBuilder(final INodeEntry node,final ExecutionContext context, final Framework framework, final Map<String, Object> configuration) {
        this.context = context;
        this.framework = framework;
        this.frameworkProject = context.getFrameworkProject();
        this.jobConf = configuration;
        this.nodes = Collections.singleton(node);
        this.tempFiles = new LinkedList<>();
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
                getjobConf()
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
                getjobConf()
                );
        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public  byte[] getPrivateKeyStorageDataBytes() throws IOException {
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
                getjobConf()
                );

        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
         return path;
    }

    public String getSshPrivateKey()  throws ConfigurationException{
        //look for storage option
        String storagePath = getPrivateKeyStoragePath();

        if(null!=storagePath){
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

    public String getSshPassword()  throws ConfigurationException{

        //look for option values first
        //typically jobs use secure options to dynamically setup the ssh password
        final String passwordOption = PropertyResolver.resolveProperty(
                    AnsibleDescribable.ANSIBLE_SSH_PASSWORD_OPTION,
                    AnsibleDescribable.DEFAULT_ANSIBLE_SSH_PASSWORD_OPTION,
                    getFrameworkProject(),
                    getFramework(),
                    getNode(),
                    getjobConf()
                    );
        String sshPassword = PropertyResolver.evaluateSecureOption(passwordOption, getContext());

        if(null!=sshPassword){
            // is true if there is an ssh option defined in the private data context
            return sshPassword;
        } else {
            //look for storage option
            String storagePath = getPasswordStoragePath();

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
                    getjobConf()
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
                  getjobConf()
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
                  getjobConf()
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
                   getFramework(),getNode(),
                   getjobConf()
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
                   getjobConf()
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
    	            getjobConf()
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
                   getjobConf()
                   );

        if (null != becomeMethod) {
             return BecomeMethodType.valueOf(becomeMethod);
        }
        return null;
    }

    public byte[] getPasswordStorageData() throws IOException{
        return loadStoragePathData(getPasswordStoragePath());
    }

    public String getBecomePasswordStoragePath() {
        String path = PropertyResolver.resolveProperty(
        		AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_STORAGE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getjobConf()
                );
        //expand properties in path
        if (path != null && path.contains("${")) {
            path = DataContextUtils.replaceDataReferencesInString(path, context.getDataContext());
        }
        return path;
    }

    public byte[] getBecomePasswordStorageData() throws IOException{
        return loadStoragePathData(getBecomePasswordStoragePath());
    }

    public String getBecomePassword()  throws ConfigurationException{

        //look for option values first
        //typically jobs use secure options to dynamically setup the become password
        String passwordOption = PropertyResolver.resolveProperty(
                    AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_OPTION,
                    AnsibleDescribable.DEFAULT_ANSIBLE_BECOME_PASSWORD_OPTION,
                    getFrameworkProject(),
                    getFramework(),
                    getNode(),
                    getjobConf()
                    );
        String becomePassword = PropertyResolver.evaluateSecureOption(passwordOption, getContext());

        if(null!=becomePassword){
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
                getjobConf()
                );

            if(null!=storagePath){
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
                getjobConf()
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
                getjobConf()
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
        if ( getjobConf().containsKey(AnsibleDescribable.ANSIBLE_PLAYBOOK_PATH) ) {
        	playbook = (String) jobConf.get(AnsibleDescribable.ANSIBLE_PLAYBOOK_PATH);
        }

        if (null != playbook && playbook.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(playbook, getContext().getDataContext());
        }
        return playbook;
    }

    public String getPlaybookInline() {
    	 	String playbook = null;
         if ( getjobConf().containsKey(AnsibleDescribable.ANSIBLE_PLAYBOOK_INLINE) ) {
         	playbook = (String) jobConf.get(AnsibleDescribable.ANSIBLE_PLAYBOOK_INLINE);
         }

         if (null != playbook && playbook.contains("${")) {
             return DataContextUtils.replaceDataReferencesInString(playbook, getContext().getDataContext());
         }
         return playbook;
    }

    public String getModule() {
        String module = null;
        if ( getjobConf().containsKey(AnsibleDescribable.ANSIBLE_MODULE) ) {
        	module = (String) jobConf.get(AnsibleDescribable.ANSIBLE_MODULE);
        }

        if (null != module && module.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(module, getContext().getDataContext());
        }
        return module;
    }

    public String getModuleArgs() {
        String args = null;
        if ( getjobConf().containsKey(AnsibleDescribable.ANSIBLE_MODULE_ARGS) ) {
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
                  getjobConf()
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
                  getjobConf()
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
                    getjobConf()
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
                  getjobConf()
                  );

        if (null != sgenerateInventory) {
        	generateInventory = Boolean.parseBoolean(sgenerateInventory);
        }
        return generateInventory;
    }

    public String getInventory() throws ConfigurationException {
        String inventory;
        String inline_inventory;
        Boolean isGenerated =  generateInventory();


        if (isGenerated !=null && isGenerated) {
            File tempInventory = new AnsibleInventoryBuilder(this.nodes).buildInventory();
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
                getjobConf()
        );

        if (inline_inventory != null) {
            /* Create tmp file with inventory */
            /*
            the builder gets the nodes from rundeck in rundeck node format and converts to ansible inventory
            we don't want that, we simply want the list we provided in ansible format
             */
            File tempInventory = new AnsibleInlineInventoryBuilder(inline_inventory).buildInventory();
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
                getjobConf()
        );

        if (null != inventory && inventory.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(inventory, getContext().getDataContext());
        }

        return inventory;
    }

    public String getLimit() throws ConfigurationException {
        final String limit;

        // Return Null if Disabled
        if(PropertyResolver.resolveBooleanProperty(
        				AnsibleDescribable.ANSIBLE_DISABLE_LIMIT,
        				Boolean.valueOf(AnsibleDescribable.DISABLE_LIMIT_PROP.getDefaultValue()),
    				    getFrameworkProject(),
                        getFramework(),
                        getNode(),
                        getjobConf())){

        	return null;
        }

        // Get Limit from Rundeck
        limit = PropertyResolver.resolveProperty(
                     AnsibleDescribable.ANSIBLE_LIMIT,
                     null,
                     getFrameworkProject(),
                     getFramework(),
                     getNode(),
                     getjobConf()
                     );

        if (null != limit && limit.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(limit, getContext().getDataContext());
        }
        return limit;
    }

    public String getConfigFile() {

        final String configFile;
        configFile = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_CONFIG_FILE_PATH,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getjobConf()
        );

        if (null != configFile && configFile.contains("${")) {
            return DataContextUtils.replaceDataReferencesInString(configFile, getContext().getDataContext());
        }
        return configFile;
    }

    public String getBaseDir() {
        String baseDir = null;
        if ( getjobConf().containsKey(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH) ) {
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
		getjobConf()
		);	
        if (null != binariesFilePathStr) {
            if (binariesFilePathStr.contains("${")) {
                return DataContextUtils.replaceDataReferencesInString(binariesFilePathStr, getContext().getDataContext());
            }
        }
        return binariesFilePathStr;
    }

    public AnsibleRunner buildAnsibleRunner() throws ConfigurationException{

        AnsibleRunner runner = null;

        String playbook;
        String module;

        if ((playbook = getPlaybookPath()) != null) {
            runner = AnsibleRunner.playbookPath(playbook);
        } else if ((playbook = getPlaybookInline()) != null) {
        		runner = AnsibleRunner.playbookInline(playbook);
        } else if ((module  = getModule()) != null) {
            runner = AnsibleRunner.adHoc(module, getModuleArgs());
        } else {
            throw new ConfigurationException("Missing module or playbook job arguments");
        }

        final AuthenticationType authType = getSshAuthenticationType();
        if (AuthenticationType.privateKey == authType) {
             final String privateKey = getSshPrivateKey();
             if (privateKey != null) {
                runner = runner.sshPrivateKey(privateKey);
             }

             if(getUseSshAgent()){
                 runner.sshUseAgent(true);

                 String passphraseOption = getPassphrase();
                 runner.sshPassphrase(passphraseOption);
             }



        } else if (AuthenticationType.password == authType) {
            final String password = getSshPassword();
            if (password != null) {
                runner = runner.sshUsePassword(Boolean.TRUE).sshPass(password);
            }
        }

        // set rundeck options as environment variables
        Map<String,String> options = context.getDataContext().get("option");
        if (options != null) {
            runner = runner.options(options);
        }

        String inventory = getInventory();
        if (inventory != null) {
            runner = runner.setInventory(inventory);
        }

        String limit = getLimit();
        if (limit != null) {
            runner = runner.limit(limit);
        }

        Boolean debug = getDebug();
        if (debug != null) {
            if (debug == Boolean.TRUE) {
               runner = runner.debug(Boolean.TRUE);
            } else {
               runner = runner.debug(Boolean.FALSE);
            }
        }

        String extraParams = getExtraParams();
        if (extraParams != null) {
             runner = runner.extraParams(extraParams);
        }

        String extraVars = getExtraVars();
        if (extraVars != null) {
            runner = runner.extraVars(extraVars);
        }

        String user = getSshUser();
        if (user != null) {
            runner = runner.sshUser(user);
        }

        String vault = getVaultKey();
        if (vault != null) {
            runner = runner.vaultPass(vault);
        }

        Integer timeout = getSSHTimeout();
        if (timeout != null) {
            runner = runner.sshTimeout(timeout);
        }

        Boolean become = getBecome();
        if (become != null) {
            runner = runner.become(become);
        }

        String become_user = getBecomeUser();
        if (become_user != null) {
            runner = runner.becomeUser(become_user);
        }

        BecomeMethodType become_method = getBecomeMethod();
        if (become_method != null) {
            runner = runner.becomeMethod(become_method.name());
        }

        String become_password = getBecomePassword();
        if (become_password != null) {
            runner = runner.becomePassword(become_password);
        }

        String executable = getExecutable();
        if (executable != null) {
            runner = runner.executable(executable);
        }

        String configFile = getConfigFile();
        if (configFile != null) {
            runner = runner.configFile(configFile);
        }

        String baseDir = getBaseDir();
        if (baseDir != null) {
            runner = runner.baseDirectory(baseDir);
        }

        String binariesFilePath = getBinariesFilePath();
        if (binariesFilePath != null) {
            runner = runner.ansibleBinariesDirectory(binariesFilePath);
        }

        boolean encryptTempFiles = encryptTempFiles();
        if(encryptTempFiles){
            runner = runner.encryptTemporaryFiles(true);
        }

        return runner;
    }

    public ExecutionContext getContext() {
        return context;
    }

    public Framework getFramework() {
        return framework;
    }

    public INodeEntry getNode() {
        return nodes.size() == 1 ? nodes.iterator().next() : null;
    }

    public String getFrameworkProject() {
        return frameworkProject;
    }

    public Map<String,Object> getjobConf() {
        return jobConf;
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
                getjobConf()
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
                getjobConf()
        );
        String sshPassword = PropertyResolver.evaluateSecureOption(passphraseOption, getContext());

        if(null!=sshPassword){
            // is true if there is an ssh option defined in the private data context
            return sshPassword;
        }else{
            return getPassphraseStorageData(getPassphraseStoragePath());
        }
    }

    public String getPassphraseStoragePath(){

        String storagePath = PropertyResolver.resolveProperty(
                AnsibleDescribable.ANSIBLE_SSH_PASSPHRASE,
                null,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getjobConf()
        );

        if(null!=storagePath) {
            //expand properties in path
            if (storagePath.contains("${")) {
                storagePath = DataContextUtils.replaceDataReferencesInString(storagePath, context.getDataContext());
            }

            return storagePath;
        }

        return null;

    }
    
    public String getPassphraseStorageData(String storagePath) throws ConfigurationException {
        if(storagePath == null){
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

    public boolean encryptTempFiles() throws ConfigurationException {
        return PropertyResolver.resolveBooleanProperty(
                AnsibleDescribable.ENCRYPT_TEMP_FILES,
                false,
                getFrameworkProject(),
                getFramework(),
                getNode(),
                getjobConf()
        );
    }
}
