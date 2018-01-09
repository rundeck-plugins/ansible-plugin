package com.batix.rundeck.plugins;

import com.batix.rundeck.core.AnsibleDescribable;
import com.batix.rundeck.core.AnsibleDescribable.AuthenticationType;
import com.batix.rundeck.core.AnsibleException;
import com.batix.rundeck.core.AnsibleRunner;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.common.NodeSetImpl;
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.dtolabs.rundeck.core.plugins.ScriptDataContextUtil;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class AnsibleResourceModelSource implements ResourceModelSource {

  private Framework framework;
  
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

  protected String vaultPass;
  protected Boolean debug = false;

  // ansible ssh args
  protected String sshUser;
  protected Boolean sshUsePassword;
  protected String sshPassword;
  protected String sshPrivateKeyFile;
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

  public void configure(Properties configuration) throws ConfigurationException {

    project = configuration.getProperty("project");
    configDataContext = new HashMap<String, Map<String, String>>();
    final HashMap<String, String> configdata = new HashMap<String, String>();
    configdata.put("project", project);
    configDataContext.put("context", configdata);
    executionDataContext = ScriptDataContextUtil.createScriptDataContextForProject(framework, project);
    executionDataContext.putAll(configDataContext);

    inventory = resolveProperty(AnsibleDescribable.ANSIBLE_INVENTORY,null,configuration,executionDataContext);
    gatherFacts = "true".equals(resolveProperty(AnsibleDescribable.ANSIBLE_GATHER_FACTS,null,configuration,executionDataContext));
    ignoreErrors = "true".equals(resolveProperty(AnsibleDescribable.ANSIBLE_IGNORE_ERRORS,null,configuration,executionDataContext));
    
    limit = (String) resolveProperty(AnsibleDescribable.ANSIBLE_LIMIT,null,configuration,executionDataContext);
    ignoreTagPrefix = (String) resolveProperty(AnsibleDescribable.ANSIBLE_IGNORE_TAGS,null,configuration,executionDataContext);

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
          throw new ConfigurationException("Can't parse timeout value : " + e.getMessage());
       }
    }
    
    become = "true".equals( resolveProperty(AnsibleDescribable.ANSIBLE_BECOME,null,configuration,executionDataContext) );
    becomeMethod = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_METHOD,null,configuration,executionDataContext);
    becomeUser = (String) resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_USER,null,configuration,executionDataContext);
    becomePassword = (String)  resolveProperty(AnsibleDescribable.ANSIBLE_BECOME_PASSWORD,null,configuration,executionDataContext);


    configFile = (String)  resolveProperty(AnsibleDescribable.ANSIBLE_CONFIG_FILE_PATH,null,configuration,executionDataContext);
    vaultFile = (String) resolveProperty(AnsibleDescribable.ANSIBLE_VAULT_PATH,null,configuration,executionDataContext);
    vaultPassword = (String) resolveProperty(AnsibleDescribable.ANSIBLE_VAULT_PASSWORD,null,configuration,executionDataContext);


  }

  public AnsibleRunner buildAnsibleRunner() throws ResourceModelSourceException{

	  AnsibleRunner runner = AnsibleRunner.playbookPath("gather-hosts.yml");

	  if ("true".equals(System.getProperty("ansible.debug"))) {
		  runner.debug();
	  }

	  if (limit != null && limit.length() > 0) {
		  List<String> limitList = new ArrayList<>();
		  limitList.add(limit);
		  runner.limit(limitList);
	  }
	  
		  if ( sshAuthType.equalsIgnoreCase(AuthenticationType.privateKey.name()) ) {
			  if (sshPrivateKeyFile != null) {
				  String sshPrivateKey;
				  try {
					  sshPrivateKey = new String(Files.readAllBytes(Paths.get(sshPrivateKeyFile)));
				  } catch (IOException e) {
					  throw new ResourceModelSourceException("Could not read privatekey file " + sshPrivateKeyFile,e);
				  }
				  runner = runner.sshPrivateKey(sshPrivateKey);
			  }
		  } else if ( sshAuthType.equalsIgnoreCase(AuthenticationType.password.name()) ) {
			  if (sshPassword != null) {
				  runner = runner.sshUsePassword(Boolean.TRUE).sshPass(sshPassword);
			  }
		  }
	  

	  if (inventory != null) {
		  runner = runner.setInventory(inventory);
	  }

	  if (ignoreErrors == true) {
		  runner = runner.ignoreErrors(ignoreErrors);
	  }

	  if (sshUser != null) {
		  runner = runner.sshUser(sshUser);
	  }
	  if (sshTimeout != null) {
		  runner = runner.sshTimeout(sshTimeout);
	  }

	  if (become != null) {
		  runner = runner.become(become);
	  }

	  if (becomeUser != null) {
		  runner = runner.becomeUser(becomeUser);
	  }

	  if (becomeMethod != null) {
		  runner = runner.becomeMethod(becomeMethod);
	  }

	  if (becomePassword != null) {
		  runner = runner.becomePassword(becomePassword);
	  }

      if (configFile != null) {
        runner = runner.configFile(configFile);
      }

      if(vaultPassword!=null) {
        runner.vaultPass(vaultPassword);
      }

      if (vaultFile != null) {
        String vaultPassword;
        try {
          vaultPassword = new String(Files.readAllBytes(Paths.get(vaultFile)));
        } catch (IOException e) {
          throw new ResourceModelSourceException("Could not read vault file " + vaultFile,e);
        }
        runner.vaultPass(vaultPassword);
      }

	  return runner;
  }


  @Override
  public INodeSet getNodes() throws ResourceModelSourceException {
    NodeSetImpl nodes = new NodeSetImpl();

    Path tempDirectory;
    try {
      tempDirectory = Files.createTempDirectory("ansible-hosts");
    } catch (IOException e) {
        throw new ResourceModelSourceException("Error creating temporary directory.", e);
    }

    try {
      Files.copy(this.getClass().getClassLoader().getResourceAsStream("host-tpl.j2"), tempDirectory.resolve("host-tpl.j2"));
      Files.copy(this.getClass().getClassLoader().getResourceAsStream("gather-hosts.yml"), tempDirectory.resolve("gather-hosts.yml"));
    } catch (IOException e) {
        throw new ResourceModelSourceException("Error copying files.");
    }

    AnsibleRunner runner = buildAnsibleRunner();

    runner.tempDirectory(tempDirectory).retainTempDirectory();

    StringBuilder args = new StringBuilder();
    args.append("facts: ")
        .append(gatherFacts ? "True" : "False")
        .append("\n")
        .append("tmpdir: '")
        .append(tempDirectory.toFile().getAbsolutePath())
        .append("'");
    
    runner.extraVars(args.toString());

    try {
        runner.run();
    } catch (AnsibleException e) {
        throw new ResourceModelSourceException(e.getMessage(), e);
    } catch (Exception e) {
        throw new ResourceModelSourceException(e.getMessage(),e);
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

          String nodename = root.get("inventory_hostname").getAsString();

          node.setHostname(hostname);
          node.setNodename(nodename);

          String username = System.getProperty("user.name"); // TODO better default?
          if (root.has("ansible_user")) {
            username = root.get("ansible_user").getAsString();
          } else if (root.has("ansible_ssh_user")) { // deprecated variable
            username = root.get("ansible_ssh_user").getAsString();
          } else if (root.has("ansible_user_id")) { // fact
            username = root.get("ansible_user_id").getAsString();
          }
          node.setUsername(username);

          HashSet<String> tags = new HashSet<>();
          for (JsonElement ele : root.getAsJsonArray("group_names")) {
            if (ignoreTagPrefix != null && ignoreTagPrefix.length() > 0 && ele.getAsString().startsWith(ignoreTagPrefix)) continue;
            tags.add(ele.getAsString());
          }
          if (extraTag != null && extraTag.length() > 0) {
        	tags.add(extraTag);
          }
          node.setTags(tags);

          if (root.has("ansible_lsb") && root.getAsJsonObject("ansible_lsb").has("description")) {
            node.setDescription(root.getAsJsonObject("ansible_lsb").get("description").getAsString());
          } else {
            StringBuilder sb = new StringBuilder();

            if (root.has("ansible_distribution")) {
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

          if (root.has("ansible_os_name")) {
            node.setOsName(root.get("ansible_os_name").getAsString());
          }

          if (root.has("ansible_architecture")) {
            node.setOsArch(root.get("ansible_architecture").getAsString());
          }

          if (root.has("ansible_kernel")) {
            node.setOsVersion(root.get("ansible_kernel").getAsString());
          }

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

          nodes.putNode(node);
        }
        directoryStream.close();
      }
    } catch (IOException e) {
      throw new ResourceModelSourceException("Error reading facts.", e);
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
      throw new ResourceModelSourceException("Error deleting temporary directory.", e);
    }

    return nodes;
  }
}
