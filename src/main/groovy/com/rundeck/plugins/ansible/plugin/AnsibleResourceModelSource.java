package com.rundeck.plugins.ansible.plugin;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.common.NodeSetImpl;
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils;
import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin;
import com.dtolabs.rundeck.core.plugins.ScriptDataContextUtil;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import com.dtolabs.rundeck.core.storage.keys.KeyStorageTree;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable.AuthenticationType;
import com.rundeck.plugins.ansible.ansible.AnsibleException;
import com.rundeck.plugins.ansible.ansible.AnsibleInventoryList;
import com.rundeck.plugins.ansible.ansible.AnsibleRunner;
import com.rundeck.plugins.ansible.ansible.InventoryList;
import com.rundeck.plugins.ansible.util.AnsibleUtil;
import com.rundeck.plugins.ansible.util.VaultPrompt;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.rundeck.app.spi.Services;
import org.rundeck.storage.api.PathUtil;
import org.rundeck.storage.api.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import static com.rundeck.plugins.ansible.ansible.AnsibleDescribable.ANSIBLE_YAML_DATA_SIZE;
import static com.rundeck.plugins.ansible.ansible.AnsibleDescribable.ANSIBLE_YAML_MAX_ALIASES;
import static com.rundeck.plugins.ansible.ansible.InventoryList.ALL;
import static com.rundeck.plugins.ansible.ansible.InventoryList.CHILDREN;
import static com.rundeck.plugins.ansible.ansible.InventoryList.HOSTS;
import static com.rundeck.plugins.ansible.ansible.InventoryList.NodeTag;

@Slf4j
public class AnsibleResourceModelSource implements ResourceModelSource, ProxyRunnerPlugin {

  private static final Logger logger = LoggerFactory.getLogger(AnsibleResourceModelSource.class);
  public static final String HOST_TPL_J2 = "host-tpl.j2";
  public static final String GATHER_HOSTS_YML = "gather-hosts.yml";

  @Setter
  private Framework framework;

  @Setter
  private Services services;

  private String project;
  private String sshAuthType;

  private HashMap<String, Map<String, String>> configDataContext;
  private Map<String, Map<String, String>> executionDataContext;

  private String inventory;
  private boolean gatherFacts;
  private boolean ignoreErrors = false;
  private String limit;
  private String ignoreTagPrefix;
  private String extraTag;
  private boolean importInventoryVars;
  private String ignoreInventoryVars;

  protected String vaultPass;
  protected Boolean debug = false;

  // ansible ssh args
  protected String sshUser;
  protected Boolean sshUsePassword;
  protected String sshPassword;
  protected String sshPrivateKeyFile;

  protected String sshPasswordPath;
  protected String sshPrivateKeyPath;
  protected String sshPass;
  protected Integer sshTimeout;

  // ansible become args
  protected Boolean become;
  protected String becomeMethod;
  protected String becomeUser;
  protected String becomePassword;
  protected String configFile;

  protected String vaultFile;
  protected String vaultPassword;

  protected String vaultPasswordPath;

  protected String baseDirectoryPath;

  protected String ansibleBinariesDirectoryPath;

  protected String extraParameters;

  protected String sshAgent;
  protected String sshPassphraseStoragePath;

  protected String becamePasswordStoragePath;

  protected boolean encryptExtraVars = false;

  protected  String customTmpDirPath;

  @Setter
  private Integer yamlDataSize;
  @Setter
  private Integer yamlMaxAliases;

  @Setter
  private AnsibleInventoryList.AnsibleInventoryListBuilder ansibleInventoryListBuilder = null;

  private Map<String, NodeEntryImpl> ansibleNodes = new HashMap<>();

  public AnsibleResourceModelSource(final Framework framework) {
      this.framework = framework;
  }

  private static String resolveProperty(
            final String attribute,
            final String defaultValue,
            final Properties configuration,
            final Map<String, Map<String, String>> dataContext
  )
  {
        if ( configuration.containsKey(attribute) ) {
            return DataContextUtils.replaceDataReferences( (String)configuration.get(attribute),dataContext);
        } else {
          return defaultValue;
        }
  }

  private static Integer resolveIntProperty(
          final String attribute,
          final Integer defaultValue,
          final Properties configuration,
          final Map<String, Map<String, String>> dataContext) throws ConfigurationException {
      final String strValue = resolveProperty(attribute, null, configuration, dataContext);
      if (null != strValue) {
          try {
              return Integer.parseInt(strValue);
          } catch (NumberFormatException e) {
              throw new ConfigurationException("Can't parse attribute :" + attribute +
                      ", value: " + strValue +
                      " Expected Integer. : " + e.getMessage(), e);
          }
      }
      return defaultValue;
  }

  private static Boolean skipVar(final String hostVar, final List<String> varList) {
    for (final String specialVarString : varList) {
      if (hostVar.startsWith(specialVarString)) return true;
    }
    return false;
  }

    public void configure(Properties configuration) throws ConfigurationException {

    project = configuration.getProperty("project");
    configDataContext = new HashMap<String, Map<String, String>>();
    final HashMap<String, String> configdata = new HashMap<String, String>();
    configdata.put("project", project);
    configDataContext.put("context", configdata);
    executionDataContext = ScriptDataContextUtil.createScriptDataContextForProject(framework, project);
    executionDataContext.putAll(configDataContext);
    customTmpDirPath = AnsibleUtil.getCustomTmpPathDir(framework);
    inventory = resolveProperty(AnsibleDescribable.ANSIBLE_INVENTORY,null,configuration,executionDataContext);
    gatherFacts = "true".equals(resolveProperty(AnsibleDescribable.ANSIBLE_GATHER_FACTS,null,configuration,executionDataContext));
    ignoreErrors = "true".equals(resolveProperty(AnsibleDescribable.ANSIBLE_IGNORE_ERRORS,null,configuration,executionDataContext));

    limit = (String) resolveProperty(AnsibleDescribable.ANSIBLE_LIMIT,null,configuration,executionDataContext);
    ignoreTagPrefix = (String) resolveProperty(AnsibleDescribable.ANSIBLE_IGNORE_TAGS,null,configuration,executionDataContext);

    importInventoryVars = "true".equals(resolveProperty(AnsibleDescribable.ANSIBLE_IMPORT_INVENTORY_VARS,null,configuration,executionDataContext));
    ignoreInventoryVars = (String) resolveProperty(AnsibleDescribable.ANSIBLE_IGNORE_INVENTORY_VARS,null,configuration,executionDataContext);

    extraTag = (String) resolveProperty(AnsibleDescribable.ANSIBLE_EXTRA_TAG,null,configuration,executionDataContext);

    sshAuthType = resolveProperty(AnsibleDescribable.ANSIBLE_SSH_AUTH_TYPE,AuthenticationType.privateKey.name(),configuration,executionDataContext);

    sshUser = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_USER,null,configuration,executionDataContext);

    sshPrivateKeyFile = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_KEYPATH,null,configuration,executionDataContext);

    sshPassword = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_PASSWORD,null,configuration,executionDataContext);

    sshTimeout = null;
    String str_sshTimeout = resolveProperty(AnsibleDescribable.ANSIBLE_SSH_TIMEOUT,null,configuration,executionDataContext);
    if ( str_sshTimeout != null ) {
       try {
          sshTimeout =  Integer.parseInt(str_sshTimeout);
       } catch (NumberFormatException e) {
          throw new ConfigurationException("Can't parse timeout value : " + e.getMessage(), e);
       }
    }

    become = "true".equals( resolveProperty(AnsibleDescribable.ANSIBLE_BECOME,null,configuration,executionDataContext) );
    becomeMethod = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_METHOD,null,configuration,executionDataContext);
    becomeUser = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_USER,null,configuration,executionDataContext);
    becomePassword = (String)  resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_PASSWORD,null,configuration,executionDataContext);


    configFile = (String)  resolveProperty(AnsibleDescribable.ANSIBLE_CONFIG_FILE_PATH,null,configuration,executionDataContext);

    vaultFile = (String) resolveProperty(AnsibleDescribable.ANSIBLE_VAULT_PATH,null,configuration,executionDataContext);
    vaultPassword = (String) resolveProperty(AnsibleDescribable.ANSIBLE_VAULT_PASSWORD,null,configuration,executionDataContext);

    baseDirectoryPath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH,null,configuration,executionDataContext);

    ansibleBinariesDirectoryPath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BINARIES_DIR_PATH, null, configuration, executionDataContext);

    extraParameters = (String)  resolveProperty(AnsibleDescribable.ANSIBLE_EXTRA_PARAM,null,configuration,executionDataContext);

    sshPasswordPath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_PASSWORD_STORAGE_PATH,null,configuration,executionDataContext);
    sshPrivateKeyPath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_KEYPATH_STORAGE_PATH,null,configuration,executionDataContext);

    vaultPasswordPath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_VAULTSTORE_PATH,null,configuration,executionDataContext);

    sshAgent = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_USE_AGENT,null,configuration,executionDataContext);
    sshPassphraseStoragePath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_SSH_PASSPHRASE,null,configuration,executionDataContext);

    becamePasswordStoragePath = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_STORAGE_PATH,null,configuration,executionDataContext);

    encryptExtraVars = "true".equals(resolveProperty(AnsibleDescribable.ANSIBLE_ENCRYPT_EXTRA_VARS,"false",configuration,executionDataContext));

    // Inventory Yaml
    yamlDataSize = resolveIntProperty(ANSIBLE_YAML_DATA_SIZE,10, configuration, executionDataContext);
    yamlMaxAliases = resolveIntProperty(ANSIBLE_YAML_MAX_ALIASES,1000, configuration, executionDataContext);

  }

  public AnsibleRunner.AnsibleRunnerBuilder buildAnsibleRunner() throws ResourceModelSourceException {

    AnsibleRunner.AnsibleRunnerBuilder runnerBuilder = AnsibleRunner.playbookPath(GATHER_HOSTS_YML);

    if ("true".equals(System.getProperty("ansible.debug"))) {
      runnerBuilder.debug(true);
    }
    runnerBuilder.customTmpDirPath(AnsibleUtil.getCustomTmpPathDir(framework));

    if (limit != null && limit.length() > 0) {
      List<String> limitList = new ArrayList<>();
      limitList.add(limit);
      runnerBuilder.limits(limitList);
    }

    StorageTree storageTree = services.getService(KeyStorageTree.class);


    if ( sshAuthType.equalsIgnoreCase(AuthenticationType.privateKey.name()) ) {
      if (sshPrivateKeyFile != null) {
        String sshPrivateKey;
        try {
          sshPrivateKey = new String(Files.readAllBytes(Paths.get(sshPrivateKeyFile)));
        } catch (IOException e) {
          throw new ResourceModelSourceException("Could not read privatekey file " + sshPrivateKeyFile +": "+ e.getMessage(),e);
        }
        runnerBuilder.sshPrivateKey(sshPrivateKey);
      }

      if(sshPrivateKeyPath !=null && !sshPrivateKeyPath.isEmpty()){
        try {
          String sshPrivateKey = getStorageContentString(sshPrivateKeyPath, storageTree);
          runnerBuilder.sshPrivateKey(sshPrivateKey);
        } catch (ConfigurationException e) {
          throw new ResourceModelSourceException("Could not read private key from storage path " + sshPrivateKeyPath +": "+ e.getMessage(),e);
        }
      }

      if(sshAgent != null && sshAgent.equalsIgnoreCase("true")) {
        runnerBuilder.sshUseAgent(Boolean.TRUE);

        if(sshPassphraseStoragePath != null && !sshPassphraseStoragePath.isEmpty()) {
          try {
            String sshPassphrase = getStorageContentString(sshPassphraseStoragePath, storageTree);
            runnerBuilder.sshPassphrase(sshPassphrase);
          } catch (ConfigurationException e) {
            throw new ResourceModelSourceException("Could not read passphrase from storage path " + sshPassphraseStoragePath +": "+ e.getMessage(),e);
          }
        }
      }

    } else if ( sshAuthType.equalsIgnoreCase(AuthenticationType.password.name()) ) {
      if (sshPassword != null) {
        runnerBuilder.sshUsePassword(Boolean.TRUE).sshPass(sshPassword);
      }

      if(sshPasswordPath !=null && !sshPasswordPath.isEmpty()){
        try {
          sshPassword = getStorageContentString(sshPasswordPath, storageTree);
          runnerBuilder.sshUsePassword(Boolean.TRUE).sshPass(sshPassword);
        } catch (ConfigurationException e) {
          throw new ResourceModelSourceException("Could not read password from storage path " + sshPasswordPath +": "+ e.getMessage(),e);
        }
      }
    }

    if (inventory != null) {
      runnerBuilder.inventory(inventory);
    }

    if (ignoreErrors == true) {
      runnerBuilder.ignoreErrors(ignoreErrors);
    }

    if (sshUser != null) {
      runnerBuilder.sshUser(sshUser);
    }
    if (sshTimeout != null) {
      runnerBuilder.sshTimeout(sshTimeout);
    }

    if (become != null) {
      runnerBuilder.become(become);
    }

    if (becomeUser != null) {
      runnerBuilder.becomeUser(becomeUser);
    }

    if (becomeMethod != null) {
      runnerBuilder.becomeMethod(becomeMethod);
    }

    if (becomePassword != null) {
      runnerBuilder.becomePassword(becomePassword);
    }

    if(becamePasswordStoragePath != null && !becamePasswordStoragePath.isEmpty()){
      try {
        becomePassword = getStorageContentString(becamePasswordStoragePath, storageTree);
        runnerBuilder.becomePassword(becomePassword);
      } catch (Exception e) {
        throw new ResourceModelSourceException("Could not read becomePassword from storage path " + becamePasswordStoragePath +": "+ e.getMessage(),e);
      }
    }

    if (configFile != null) {
      runnerBuilder.configFile(configFile);
    }

    if(vaultPassword!=null) {
      runnerBuilder.vaultPass(vaultPassword);
    }

    if(vaultPasswordPath!=null && !vaultPasswordPath.isEmpty()){
      try {
        vaultPassword = getStorageContentString(vaultPasswordPath, storageTree);
      } catch (Exception e) {
        throw new ResourceModelSourceException("Could not read vaultPassword " + vaultPasswordPath +": "+ e.getMessage(),e);
      }
      runnerBuilder.vaultPass(vaultPassword);
    }

    if (vaultFile != null) {
      String vaultPassword;
      try {
        vaultPassword = new String(Files.readAllBytes(Paths.get(vaultFile)));
      } catch (IOException e) {
        throw new ResourceModelSourceException("Could not read vault file " + vaultFile +": "+ e.getMessage(),e);
      }
      runnerBuilder.vaultPass(vaultPassword);
    }
    if (baseDirectoryPath != null) {
        runnerBuilder.baseDirectory(java.nio.file.Path.of(baseDirectoryPath));
    }

    if (ansibleBinariesDirectoryPath != null) {
      runnerBuilder.ansibleBinariesDirectory(java.nio.file.Path.of(ansibleBinariesDirectoryPath));
    }

    if (extraParameters != null){
      runnerBuilder.extraParams(extraParameters);
    }

    runnerBuilder.encryptExtraVars(encryptExtraVars);

    return runnerBuilder;
  }

  @Override
  public INodeSet getNodes() throws ResourceModelSourceException {
    NodeSetImpl nodes = new NodeSetImpl();
    AnsibleRunner.AnsibleRunnerBuilder runnerBuilder = buildAnsibleRunner();

    if (gatherFacts) {
      processWithGatherFacts(nodes, runnerBuilder);
    } else {
      ansibleInventoryList(nodes, runnerBuilder);
    }

    return nodes;
  }

  public void processWithGatherFacts(NodeSetImpl nodes, AnsibleRunner.AnsibleRunnerBuilder runnerBuilder) throws ResourceModelSourceException {

    final Gson gson = new Gson();
    Path tempDirectory;
    try {
      tempDirectory = Files.createTempDirectory(Path.of(customTmpDirPath),"ansible-hosts");
    } catch (IOException e) {
      throw new ResourceModelSourceException("Error creating temporary directory: " + e.getMessage(), e);
    }

    try {
      Files.copy(this.getClass().getClassLoader().getResourceAsStream(HOST_TPL_J2), tempDirectory.resolve(HOST_TPL_J2));
      Files.copy(this.getClass().getClassLoader().getResourceAsStream(GATHER_HOSTS_YML), tempDirectory.resolve(GATHER_HOSTS_YML));
    } catch (IOException e) {
      throw new ResourceModelSourceException("Error copying files: " + e.getMessage(), e);
    }
    runnerBuilder.customTmpDirPath(customTmpDirPath);
    runnerBuilder.tempDirectory(tempDirectory);
    runnerBuilder.retainTempDirectory(true);

    StringBuilder args = new StringBuilder();
    args.append("facts: ")
            .append(gatherFacts ? "True" : "False")
            .append("\n")
            .append("tmpdir: '")
            .append(tempDirectory.toFile().getAbsolutePath())
            .append("'");

    runnerBuilder.extraVars(args.toString());

    AnsibleRunner runner = runnerBuilder.build();

    try {
      runner.run();
    } catch (Exception e) {
      throw new ResourceModelSourceException("Failed Ansible Runner execution: " + e.getMessage(),e);
    }

    try {
      if (new File(tempDirectory.toFile(), "data").exists()) {
        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(tempDirectory.resolve("data"));
        for (Path factFile : directoryStream) {
          NodeEntryImpl node = new NodeEntryImpl();

          BufferedReader bufferedReader = Files.newBufferedReader(factFile, Charset.forName("utf-8"));
          JsonElement json = new JsonParser().parse(bufferedReader);
          bufferedReader.close();
          JsonObject root = json.getAsJsonObject();

          String hostname = root.get("inventory_hostname").getAsString();
          try {
            if (root.has("ansible_host")) {
              hostname = root.get("ansible_host").getAsString();
            } else if (root.has("ansible_ssh_host")) { // deprecated variable
              hostname = root.get("ansible_ssh_host").getAsString();
            }
          }catch(Exception ex){
            System.out.println("[warn] Problem getting the ansible_host attribute from node " + hostname);
          }
          node.setHostname(hostname);

          String nodename = root.get("inventory_hostname").getAsString();
          node.setNodename(nodename);

          String username = sshUser; // Use sshUser as default username
          if (root.has("ansible_user")) {
            username = root.get("ansible_user").getAsString();
          } else if (root.has("ansible_ssh_user")) { // deprecated variable
            username = root.get("ansible_ssh_user").getAsString();
          } else if (root.has("ansible_user_id")) { // fact
            username = root.get("ansible_user_id").getAsString();
          }
          node.setUsername(username);

          // Add groups as tags, except ignored tag prefix
          HashSet<String> tags = new HashSet<>();
          for (JsonElement ele : root.getAsJsonArray("group_names")) {
            if (ignoreTagPrefix != null && ignoreTagPrefix.length() > 0 && ele.getAsString().startsWith(ignoreTagPrefix)) continue;
            tags.add(ele.getAsString());
          }
          // Add extraTag to node
          if (extraTag != null && extraTag.length() > 0) {
            tags.add(extraTag);
          }
          node.setTags(tags);

          if (root.has("ansible_lsb") && root.getAsJsonObject("ansible_lsb").has("description")) {
            node.setDescription(root.getAsJsonObject("ansible_lsb").get("description").getAsString());
          } else {
            StringBuilder sb = new StringBuilder();

            if (root.has("ansible_distribution") && !root.get("ansible_distribution").isJsonNull()) {
              sb.append(root.get("ansible_distribution").getAsString()).append(" ");
            }
            if (root.has("ansible_distribution_version")) {
              sb.append(root.get("ansible_distribution_version").getAsString()).append(" ");
            }

            if (sb.length() > 0) {
              node.setDescription(sb.toString().trim());
            }
          }

          // ansible_system     = Linux   = osFamily in Rundeck
          // ansible_os_family  = Debian  = osName in Rundeck

          if (root.has("ansible_os_family")) {
            node.setOsFamily(root.get("ansible_os_family").getAsString());
          }

          if (root.has("ansible_os_name") && !root.get("ansible_os_name").isJsonNull()) {
            node.setOsName(root.get("ansible_os_name").getAsString());
          }

          if (root.has("ansible_architecture") && !root.get("ansible_architecture").isJsonNull()) {
            node.setOsArch(root.get("ansible_architecture").getAsString());
          }

          if (root.has("ansible_kernel")) {
            node.setOsVersion(root.get("ansible_kernel").getAsString());
          }

          // Add Ansible interesting vars as node attributes
          // JSON-Path -> Attribute-Name
          Map<String, String> interestingItems = new HashMap<>();

          interestingItems.put("ansible_form_factor", "form_factor");

          interestingItems.put("ansible_system_vendor", "system_vendor");

          interestingItems.put("ansible_product_name", "product_name");
          interestingItems.put("ansible_product_version", "product_version");
          interestingItems.put("ansible_product_serial", "product_serial");

          interestingItems.put("ansible_bios_version", "bios_version");
          interestingItems.put("ansible_bios_date", "bios_date");

          interestingItems.put("ansible_machine_id", "machine_id");

          interestingItems.put("ansible_virtualization_type", "virtualization_type");
          interestingItems.put("ansible_virtualization_role", "virtualization_role");

          interestingItems.put("ansible_selinux", "selinux");
          interestingItems.put("ansible_fips", "fips");

          interestingItems.put("ansible_service_mgr", "service_mgr");
          interestingItems.put("ansible_pkg_mgr", "pkg_mgr");

          interestingItems.put("ansible_distribution", "distribution");
          interestingItems.put("ansible_distribution_version", "distribution_version");
          interestingItems.put("ansible_distribution_major_version", "distribution_major_version");
          interestingItems.put("ansible_distribution_release", "distribution_release");
          interestingItems.put("ansible_lsb.codename", "lsb_codename");

          interestingItems.put("ansible_domain", "domain");

          interestingItems.put("ansible_date_time.tz", "tz");
          interestingItems.put("ansible_date_time.tz_offset", "tz_offset");

          interestingItems.put("ansible_processor_count", "processor_count");
          interestingItems.put("ansible_processor_cores", "processor_cores");
          interestingItems.put("ansible_processor_vcpus", "processor_vcpus");
          interestingItems.put("ansible_processor_threads_per_core", "processor_threads_per_core");

          interestingItems.put("ansible_userspace_architecture", "userspace_architecture");
          interestingItems.put("ansible_userspace_bits", "userspace_bits");

          interestingItems.put("ansible_memtotal_mb", "memtotal_mb");
          interestingItems.put("ansible_swaptotal_mb", "swaptotal_mb");
          interestingItems.put("ansible_processor.0", "processor0");
          interestingItems.put("ansible_processor.1", "processor1");

          for (Map.Entry<String, String> item : interestingItems.entrySet()) {
            String[] itemParts = item.getKey().split("\\.");

            if (itemParts.length > 1) {
              JsonElement ele = root;
              for (String itemPart : itemParts) {
                if (ele.isJsonArray() && itemPart.matches("^\\d+$") && ele.getAsJsonArray().size() > Integer.parseInt(itemPart)) {
                  ele = ele.getAsJsonArray().get(Integer.parseInt(itemPart));
                } else if (ele.isJsonObject() && ele.getAsJsonObject().has(itemPart)) {
                  ele = ele.getAsJsonObject().get(itemPart);
                } else {
                  ele = null;
                  break;
                }
              }

              if (ele != null && ele.isJsonPrimitive() && ele.getAsString().length() > 0) {
                node.setAttribute(item.getValue(), ele.getAsString());
              }
            } else {
              if (root.has(item.getKey())
                      && root.get(item.getKey()).isJsonPrimitive()
                      && root.get(item.getKey()).getAsString().length() > 0) {
                node.setAttribute(item.getValue(), root.get(item.getKey()).getAsString());
              }
            }
          }


          if (importInventoryVars == true) {
            // Add ALL vars as node attributes, except Ansible Special variables, as of Ansible 2.9
            // https://docs.ansible.com/ansible/latest/reference_appendices/special_variables.html
            List<String> specialVarsList = new ArrayList<>();
            specialVarsList.add("ansible_");  // most ansible vars prefix
            specialVarsList.add("discovered_interpreter_python");
            specialVarsList.add("facts");   // rundeck used to gather host_vars
            specialVarsList.add("gather_subset");
            specialVarsList.add("group_names");
            specialVarsList.add("groups");
            specialVarsList.add("hostvars");
            specialVarsList.add("inventory_dir");
            specialVarsList.add("inventory_file");
            specialVarsList.add("inventory_hostname");
            specialVarsList.add("inventory_hostname_short");
            specialVarsList.add("module_setup");
            specialVarsList.add("omit");
            specialVarsList.add("play_hosts");
            specialVarsList.add("playbook_dir");
            specialVarsList.add("role_name");
            specialVarsList.add("role_names");
            specialVarsList.add("role_path");
            specialVarsList.add("tmpdir");  // rundeck used to gather host_vars

            if (ignoreInventoryVars != null && ignoreInventoryVars.length() > 0) {
              String[] ignoreInventoryVarsStrings = ignoreInventoryVars.split(",");
              for (String ignoreInventoryVarsString: ignoreInventoryVarsStrings) {
                specialVarsList.add(ignoreInventoryVarsString.trim());
              }
            }

            // for (String hostVar : root.keySet()) {
            for (Entry<String, JsonElement> hostVar : root.entrySet()) {

              // skip Ansible special vars
              if (skipVar(hostVar.getKey(), specialVarsList)) {
                continue;
              }

              if (hostVar.getValue() instanceof JsonPrimitive && ((JsonPrimitive) hostVar.getValue()).isString()) {
                String strValue = hostVar.getValue().getAsString();

                if ((strValue.trim().startsWith("{") && strValue.trim().endsWith("}")) ||
                        (strValue.trim().startsWith("[") && strValue.trim().endsWith("]"))) {
                  try {
                    JsonElement parsed = JsonParser.parseString(strValue);
                    node.setAttribute(hostVar.getKey(), gson.toJson(parsed));
                  } catch (Exception e) {
                    node.setAttribute(hostVar.getKey(), strValue);
                  }
                } else {
                  node.setAttribute(hostVar.getKey(), strValue);
                }
              } else {
                node.setAttribute(hostVar.getKey(), gson.toJson(hostVar.getValue()));
              }

            }
          }

          nodes.putNode(node);
        }
        directoryStream.close();
      }
    } catch (IOException e) {
      throw new ResourceModelSourceException("Error reading facts: " + e.getMessage(), e);
    }

    try {
      Files.walkFileTree(tempDirectory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new ResourceModelSourceException("Error deleting temporary directory: " + e.getMessage(), e);
    }
  }

  /**
   * Process nodes coming from Ansible to convert them to Rundeck node
   * @param nodes Rundeck nodes
   * @throws ResourceModelSourceException
   */
  public void ansibleInventoryList(NodeSetImpl nodes, AnsibleRunner.AnsibleRunnerBuilder runnerBuilder) throws ResourceModelSourceException {

    int codePointLimit = yamlDataSize * 1024 * 1024;

    LoaderOptions snakeOptions = new LoaderOptions();
    // max inventory file size allowed to 10mb
    snakeOptions.setCodePointLimit(codePointLimit);
    // max aliases. Default value is 1000
    snakeOptions.setMaxAliasesForCollections(yamlMaxAliases);
    Yaml yaml = new Yaml(new SafeConstructor(snakeOptions));

    String listResp = getNodesFromInventory(runnerBuilder);

    validateAliases(listResp);

    Map<String, Object> allInventory;
    try {
      allInventory = yaml.load(listResp);
    } catch (YAMLException e) {
      throw new ResourceModelSourceException("Cannot load yaml data coming from Ansible: " + e.getMessage(), e);
    }

    Map<String, Object> all = InventoryList.getValue(allInventory, ALL);

    if (isTagMapValid(all, ALL)) {
      Map<String, Object> children = InventoryList.getValue(all, CHILDREN);
      processChildren(children, new HashSet<>());
    }

    ansibleNodes.forEach((k, node) -> nodes.putNode(node));
    ansibleNodes.clear();
  }

  /**
   * Processes the given set of nodes and populates the children map with the results.
   *
   * @param children a map to be populated with the processed children nodes
   * @param tags    a set of tags to filter the nodes
   * @throws ResourceModelSourceException if an error occurs while processing the nodes
   */
  public void processChildren(Map<String, Object> children, HashSet<String> tags) throws ResourceModelSourceException {
    if (!isTagMapValid(children, CHILDREN)) {
      return;
    }

    for (Map.Entry<String, Object> pair : children.entrySet()) {

      String hostGroup = pair.getKey();
      tags.add(hostGroup);
      Map<String, Object> hostNames = InventoryList.getType(pair.getValue());

      if (hostNames.containsKey(CHILDREN)) {
        Map<String, Object> subChildren = InventoryList.getValue(hostNames, CHILDREN);
        processChildren(subChildren, tags);
      } else {
        processHosts(hostNames, tags);
        tags.clear();
      }
    }
  }

  /**
   * Processes the hosts within the given host names map and adds them to the nodes set.
   *
   * @param hostNames the map containing host names and their attributes
   * @param tags      the set of tags to apply to the nodes
   * @throws ResourceModelSourceException if an error occurs while processing the nodes
   */
  public void processHosts(Map<String, Object> hostNames, HashSet<String> tags) throws ResourceModelSourceException {
    Map<String, Object> hosts = InventoryList.getValue(hostNames, HOSTS);

    if (!isTagMapValid(hosts, HOSTS)) {
      return;
    }

    for (Map.Entry<String, Object> hostNode : hosts.entrySet()) {
      NodeEntryImpl node = createNodeEntry(hostNode);
      addNode(node, tags);
    }
  }

  /**
   * Creates a NodeEntryImpl object from the given host node entry and tags.
   *
   * @param hostNode the entry containing the host name and its attributes
   * @return the created NodeEntryImpl object
   */
  public NodeEntryImpl createNodeEntry(Map.Entry<String, Object> hostNode) throws ResourceModelSourceException {
    NodeEntryImpl node = new NodeEntryImpl();
    String hostName = hostNode.getKey();
    node.setHostname(hostName);
    node.setNodename(hostName);
    Map<String, Object> nodeValues = InventoryList.getType(hostNode.getValue());

    applyNodeTags(node, nodeValues);
    Gson gson = new Gson();

    nodeValues.forEach((key, value) -> {
      if (value != null) {
        if (value instanceof Map || value instanceof List) {
          node.setAttribute(key, gson.toJson(value));
        } else {
          node.setAttribute(key, value.toString());
        }
      }
    });

    return node;
  }

  /**
   * Applies predefined tags to the given node based on the provided node values.
   *
   * @param node       the node to which the tags will be applied
   * @param nodeValues the map containing the node's attributes
   */
  public void applyNodeTags(NodeEntryImpl node, Map<String, Object> nodeValues) throws ResourceModelSourceException {
    InventoryList.tagHandle(NodeTag.HOSTNAME, node, nodeValues);
    InventoryList.tagHandle(NodeTag.USERNAME, node, nodeValues);
    InventoryList.tagHandle(NodeTag.OS_FAMILY, node, nodeValues);
    InventoryList.tagHandle(NodeTag.OS_NAME, node, nodeValues);
    InventoryList.tagHandle(NodeTag.OS_ARCHITECTURE, node, nodeValues);
    InventoryList.tagHandle(NodeTag.OS_VERSION, node, nodeValues);
    InventoryList.tagHandle(NodeTag.DESCRIPTION, node, nodeValues);
  }

  /**
   * Adds a node to the ansibleNodes map, merging tags if the node already exists.
   *
   * @param node The node to add.
   * @param tags The tags to associate with the node.
   */
  public void addNode(NodeEntryImpl node, Set<String> tags) {
    ansibleNodes.compute(node.getNodename(), (key, existingNode) -> {
      if (existingNode != null) {
        Set<String> mergedTags = new HashSet<>(getStringTags(existingNode));
        mergedTags.addAll(tags);
        existingNode.setTags(Set.copyOf(mergedTags));
        return existingNode;
      } else {
        node.setTags(Set.copyOf(tags));
        return node;
      }
    });
  }

  /**
   * Retrieves the tags from a node and converts them to strings.
   *
   * @param node The node whose tags are to be retrieved.
   * @return A set of strings representing the node's tags.  Returns an empty set if the node has no tags.
   */
  public Set<String> getStringTags(NodeEntryImpl node) {
    Set<String> tags = new HashSet<>();
    for (Object tag : node.getTags()) {
      tags.add(tag.toString());
    }
    return tags;
  }

  /**
   * Gets Ansible nodes from inventory
   * @return Ansible nodes
   */
  public String getNodesFromInventory(AnsibleRunner.AnsibleRunnerBuilder runnerBuilder) throws ResourceModelSourceException {

    AnsibleRunner runner = runnerBuilder.build();

    if (this.ansibleInventoryListBuilder == null) {
      Path ansibleBinPath = null;
      if (ansibleBinariesDirectoryPath != null && !ansibleBinariesDirectoryPath.isEmpty()) {
        ansibleBinPath = (java.nio.file.Path.of(ansibleBinariesDirectoryPath));
      }

      this.ansibleInventoryListBuilder = AnsibleInventoryList.builder()
              .inventory(inventory)
              .ansibleBinariesDirectory(ansibleBinPath)
              .configFile(configFile)
              .debug(debug);
    }

    if(runner.getVaultPass() != null){
      VaultPrompt vaultPrompt = VaultPrompt.builder()
              .vaultId("None")
              .vaultPassword(runner.getVaultPass() + "\n")
              .build();
      ansibleInventoryListBuilder.vaultPrompt(vaultPrompt);
    }

    if (runner.getLimits() != null) {
      ansibleInventoryListBuilder.limits(runner.getLimits());
    }

    AnsibleInventoryList inventoryList = this.ansibleInventoryListBuilder.build();
    inventoryList.setCustomTmpDirPath(customTmpDirPath);
    try {
        return inventoryList.getNodeList();
    } catch (IOException | AnsibleException e) {
      throw new ResourceModelSourceException("Failed to get node list from ansible: " + e.getMessage(), e);
    }
  }

  private String getStorageContentString(String storagePath, StorageTree storageTree) throws ConfigurationException {
    return new String(this.getStorageContent(storagePath, storageTree));
  }

  private byte[] getStorageContent(String storagePath, StorageTree storageTree) throws ConfigurationException {
    org.rundeck.storage.api.Path path = PathUtil.asPath(storagePath);
    try {
      ResourceMeta contents = storageTree.getResource(path).getContents();
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      contents.writeContent(byteArrayOutputStream);
      return byteArrayOutputStream.toByteArray();
    } catch (StorageException e) {
      throw new ConfigurationException("Failed to read the ssh private key for " +
              "storage path: " + storagePath + ": " + e.getMessage(), e);
    } catch (IOException e) {
      throw new ConfigurationException("Failed to read the ssh private key for " +
              "storage path: " + storagePath + ": " + e.getMessage(), e);
    }
  }


  @Override
  public List<String> listSecretsPathResourceModel(Map<String, Object> configuration){
    List<String> keys = new ArrayList<>();

    String passwordStoragePath = (String) configuration.get(AnsibleDescribable.ANSIBLE_SSH_PASSWORD_STORAGE_PATH);
    String privateKeyStoragePath = (String) configuration.get(AnsibleDescribable.ANSIBLE_SSH_KEYPATH_STORAGE_PATH);
    String passphraseStoragePath = (String) configuration.get(AnsibleDescribable.ANSIBLE_SSH_PASSPHRASE);
    String vaultPasswordStoragePath = (String) configuration.get(AnsibleDescribable.ANSIBLE_VAULTSTORE_PATH);
    String becamePasswordStoragePath = (String) configuration.get(AnsibleDescribable.ANSIBLE_BECOME_PASSWORD_STORAGE_PATH);

    if(passwordStoragePath!=null && !passwordStoragePath.isEmpty()){
      keys.add(passwordStoragePath);
    }

    if(privateKeyStoragePath!=null && !privateKeyStoragePath.isEmpty()){
      if(!keys.contains(privateKeyStoragePath)){
        keys.add(privateKeyStoragePath);
      }
    }

    if(passphraseStoragePath!=null && !passphraseStoragePath.isEmpty()){
      if(!keys.contains(passphraseStoragePath)){
        keys.add(passphraseStoragePath);
      }
    }

    if(vaultPasswordStoragePath!=null && !vaultPasswordStoragePath.isEmpty()){
      if(!keys.contains(vaultPasswordStoragePath)){
        keys.add(vaultPasswordStoragePath);
      }
    }

    if(becamePasswordStoragePath!=null && !becamePasswordStoragePath.isEmpty()){
      if(!keys.contains(becamePasswordStoragePath)){
        keys.add(becamePasswordStoragePath);
      }
    }

    return keys;

  }

  /**
   * Validates if a tag is empty.
   *
   * @param tagMap  The map containing the tag content.
   * @param tagName The name of the tag to validate.
   * @return True if the tag is empty, false otherwise.
   */
  private boolean isTagMapValid(Map<String, Object> tagMap, String tagName) {
    if (tagMap == null) {
      log.warn("Tag '{}' is empty!", tagName);
      return false;
    }
    return true;
  }

  /**
   * Validates whether the YAML content contains aliases that exceed the maximum allowed.
   * @param content String yaml
   */
  public void validateAliases(String content) {
    int totalAliases = StringUtils.countMatches(content, ": *");
    if (totalAliases > yamlMaxAliases) {
      log.warn("The yaml inventory received has {} aliases and the maximum allowed is {}.", totalAliases, yamlMaxAliases);
    }
  }

}
