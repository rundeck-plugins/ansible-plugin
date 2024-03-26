package com.rundeck.plugins.ansible.ansible;

import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Property;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.plugins.util.PropertyBuilder;

import java.util.Arrays;
import java.util.LinkedList;

public interface AnsibleDescribable extends Describable {

    public static enum Executable {
        bash("/bin/bash"),
        sh("/bin/sh");

    	private final String executable;

    	Executable(String executable) {
    		this.executable = executable;
    	}

    	public String getValue() {
			return executable;
		}

		public static String[] getValues() {
    	    java.util.LinkedList<String> list = new LinkedList<String>();
    	    for (Executable s : Executable.values()) {
    	        list.add(s.executable);
    	    }
    	    return list.toArray(new String[list.size()]);
    	}
    }

    public static enum WindowsExecutable {
        cmd("cmd.exe"),
        powershell("powershell.exe");

        private final String executable;

        WindowsExecutable(String executable) {
            this.executable = executable;
        }

        public String getValue() {
            return executable;
        }

        public static String[] getValues() {
            java.util.LinkedList<String> list = new LinkedList<String>();
            for (WindowsExecutable s : WindowsExecutable.values()) {
                list.add(s.executable);
            }
            return list.toArray(new String[list.size()]);
        }
    }

    public static enum AuthenticationType {
        privateKey,
        password;

    	public static String[] getValues() {
    	    java.util.LinkedList<String> list = new LinkedList<String>();
    	    for (AuthenticationType s : AuthenticationType.values()) {
    	        list.add(s.name());
    	    }
    	    return list.toArray(new String[list.size()]);
    	}
    }

    public static enum BecomeMethodType {
        doas,
        dzdo,
        ksu,
        machinectl,
        runas,
        pbrun,
        pfexec,
        sudo,
        su;

    	public static String[] getValues() {
    	    java.util.LinkedList<String> list = new LinkedList<String>();
    	    for (BecomeMethodType s : BecomeMethodType.values()) {
    	        list.add(s.name());
    	    }
    	    return list.toArray(new String[list.size()]);
    	}
    }

    public static final String SERVICE_PROVIDER_TYPE = "ansible-service";
    public static final String ANSIBLE_PLAYBOOK_PATH = "ansible-playbook";
    public static final String ANSIBLE_PLAYBOOK_INLINE = "ansible-playbook-inline";
    public static final String ANSIBLE_INVENTORY_INLINE = "ansible-inventory-inline";
    public static final String ANSIBLE_INVENTORY = "ansible-inventory";
    public static final String ANSIBLE_GENERATE_INVENTORY = "ansible-generate-inventory";
    public static final String ANSIBLE_MODULE = "ansible-module";
    public static final String ANSIBLE_MODULE_ARGS = "ansible-module-args";
    public static final String ANSIBLE_DEBUG = "ansible-debug";
    public static final String ANSIBLE_EXECUTABLE = "ansible-executable";
    public static final String ANSIBLE_WINDOWS_EXECUTABLE = "ansible-windows-executable";
    public static final String DEFAULT_ANSIBLE_EXECUTABLE = "/bin/sh";
    public static final String DEFAULT_ANSIBLE_WINDOWS_EXECUTABLE = "powershell.exe";
    public static final String ANSIBLE_GATHER_FACTS = "ansible-gather-facts";
    public static final String ANSIBLE_IGNORE_ERRORS = "ansible-ignore-errors";
    public static final String ANSIBLE_EXTRA_TAG = "ansible-extra-tag";
    public static final String ANSIBLE_LIMIT = "ansible-limit";
    public static final String ANSIBLE_DISABLE_LIMIT ="ansible-disable-limit";
    public static final String ANSIBLE_IGNORE_TAGS = "ansible-ignore-tags-prefix";
    public static final String ANSIBLE_IMPORT_INVENTORY_VARS = "ansible-import-inventory-vars";
    public static final String ANSIBLE_IGNORE_INVENTORY_VARS = "ansible-ignore-inventory-vars";
    public static final String ANSIBLE_EXTRA_VARS = "ansible-extra-vars";
    public static final String ANSIBLE_EXTRA_PARAM = "ansible-extra-param";
    public static final String ANSIBLE_VAULT_PATH = "ansible-vault-path";
    public static final String ANSIBLE_VAULTSTORE_PATH = "ansible-vault-storage-path";
    public static final String ANSIBLE_VAULT_PASSWORD = "ansible-vault-password";

    // ssh configuration
    public static final String ANSIBLE_SSH_PASSWORD = "ansible-ssh-password";
    public static final String ANSIBLE_SSH_PASSWORD_OPTION = "ansible-ssh-password-option";
    public static final String ANSIBLE_SSH_PASSWORD_STORAGE_PATH = "ansible-ssh-password-storage-path";
    public static final String DEFAULT_ANSIBLE_SSH_PASSWORD_OPTION = "ansible-ssh-password";

    public static final String ANSIBLE_SSH_KEYPATH = "ansible-ssh-keypath";
    public static final String ANSIBLE_SSH_KEYPATH_STORAGE_PATH = "ansible-ssh-key-storage-path";

    public static final String ANSIBLE_SSH_TIMEOUT = "ansible-ssh-timeout";
    public static final String ANSIBLE_SSH_USER = "ansible-ssh-user";
    public static final String ANSIBLE_SSH_AUTH_TYPE = "ansible-ssh-auth-type";

    public static final String ANSIBLE_SSH_USE_AGENT = "ansible-ssh-use-agent";
    public static final String ANSIBLE_SSH_PASSPHRASE = "ansible-ssh-passphrase-storage-path";
    public static final String ANSIBLE_SSH_PASSPHRASE_OPTION = "ansible-ssh-passphrase-option";
    public static final String DEFAULT_ANSIBLE_SSH_PASSPHRASE_OPTION = "option.password";





    // become configuration
    public static final String ANSIBLE_BECOME = "ansible-become";
    public static final String ANSIBLE_BECOME_USER = "ansible-become-user";
    public static final String ANSIBLE_BECOME_METHOD = "ansible-become-method";
    public static final String ANSIBLE_BECOME_PASSWORD = "ansible-become-password";
    public static final String ANSIBLE_BECOME_PASSWORD_OPTION = "ansible-become-password-option";
    public static final String ANSIBLE_BECOME_PASSWORD_STORAGE_PATH = "ansible-become-password-storage-path";
    public static final String DEFAULT_ANSIBLE_BECOME_PASSWORD_OPTION = "ansible-become-password";


    public static final String ANSIBLE_CONFIG_FILE_PATH = "ansible-config-file-path";
    public static final String ANSIBLE_BASE_DIR_PATH = "ansible-base-dir-path";
    public static final String ANSIBLE_BINARIES_DIR_PATH = "ansible-binaries-dir-path";

    public static final String PROJ_PROP_PREFIX = "project.";
    public static final String FWK_PROP_PREFIX = "framework.";

    public static final String ANSIBLE_ENCRYPT_EXTRA_VARS = "ansible-encrypt-extra-vars";

    public static Property PLAYBOOK_PATH_PROP = PropertyUtil.string(
    			ANSIBLE_PLAYBOOK_PATH,
              "Playbook",
              "Path to a playbook",
              true,
              null
    );

    public static Property BASE_DIR_PROP = PropertyUtil.string(
    			ANSIBLE_BASE_DIR_PATH,
              "Ansible base directory path",
              "Set ansible base directory path.",
              false,
              null
    );

    public static Property PLAYBOOK_INLINE_PROP = PropertyBuilder.builder()
    		.string(ANSIBLE_PLAYBOOK_INLINE)
    		.required(false)
    		.title("Playbook")
        .description("Inline Ansible Playbook")
        .renderingOption(StringRenderingConstants.DISPLAY_TYPE_KEY, StringRenderingConstants.DisplayType.CODE)
        .renderingOption(StringRenderingConstants.CODE_SYNTAX_MODE, "yaml")
        .renderingOption(StringRenderingConstants.CODE_SYNTAX_SELECTABLE, false)
    		.build();

    public static Property INVENTORY_INLINE_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_INVENTORY_INLINE)
            .required(false)
            .title("Inline inventory")
            .description("Provide an inline inventory.")
            .renderingOption(StringRenderingConstants.DISPLAY_TYPE_KEY, StringRenderingConstants.DisplayType.CODE)
            .renderingOption(StringRenderingConstants.CODE_SYNTAX_SELECTABLE, true)
            .build();


    public static Property MODULE_PROP = PropertyUtil.string(
              ANSIBLE_MODULE,
              "Module",
              "Module name",
              true,
              null
    );

    public static Property MODULE_ARGS_PROP = PropertyUtil.string(
        ANSIBLE_MODULE_ARGS,
        "Arguments",
        "Arguments to pass to the module (-a/--args flag)",
        false,
        null
    );

    public static Property INVENTORY_PROP = PropertyUtil.string(ANSIBLE_INVENTORY, "ansible inventory File path",
            "File Path to the ansible inventory to use, It can be blank if \"Ansible config file path\" is set  ", false, null);

    static final Property GENERATE_INVENTORY_PROP = PropertyBuilder.builder()
    .booleanType(ANSIBLE_GENERATE_INVENTORY)
    .required(false)
    .title("Generate inventory")
    .description("Generate Ansible inventory from Rundeck nodes.")
    .build();

    public static Property EXECUTABLE_PROP = PropertyUtil.freeSelect(
              ANSIBLE_EXECUTABLE,
              "Executable",
              "Change the remote shell used to execute the command. Should be an absolute path to the executable.",
              false,
              null,
              Arrays.asList(Executable.getValues())
    );

    public static Property WINDOWS_EXECUTABLE_PROP = PropertyUtil.freeSelect(
            ANSIBLE_WINDOWS_EXECUTABLE,
            "Windows Executable",
            "Change the remote shell used to execute the command on Windows Remote Nodes. Should be an absolute path to the executable.",
            false,
            null,
            Arrays.asList(WindowsExecutable.getValues())
    );

    public static Property GATHER_FACTS_PROP = PropertyUtil.bool(
              ANSIBLE_GATHER_FACTS,
              "Gather Facts",
              "Gather fresh facts before importing? (recommended)",
              true,
              "true"
    );

    public static Property IGNORE_ERRORS_PROP = PropertyUtil.bool(
              ANSIBLE_IGNORE_ERRORS,
              "Ignore Host Discovery Errors",
              "Still display Successful Hosts even if some other hosts are unreachable ?",
              true,
              "true"
    );

    public static Property LIMIT_PROP = PropertyUtil.string(
              ANSIBLE_LIMIT,
              "Limit Targets",
              "Select only specified hosts/groups from the Ansible inventory. See http://docs.ansible.com/ansible/intro_patterns.html for syntax help.",
              false,
              ""
    );

    public static Property DISABLE_LIMIT_PROP = PropertyUtil.bool(
    		 ANSIBLE_DISABLE_LIMIT,
    		 "Disable Limit",
    		 "Disables passing the --limit parameter from Rundeck into Ansible.  If you want to select hosts, you must pass the --limit in the extra arguments field.",
    		 true,
    		 "false"
    );

    public static Property IGNORE_TAGS_PREFIX_PROP = PropertyUtil.string(
              ANSIBLE_IGNORE_TAGS,
              "Ignore tags with this prefix",
              "Leave blank to import all groups as tags or specify a prefix string, groups starting with this string won't be added as tags.",
              false,
              ""
    );

    public static Property EXTRA_TAG_PROP = PropertyUtil.string(
            ANSIBLE_EXTRA_TAG,
            "Additional host tag",
            "This tag will be added to all hosts discovered by this source.",
            false,
            ""
    );

    public static Property IMPORT_INVENTORY_VARS_PROP = PropertyUtil.bool(
            ANSIBLE_IMPORT_INVENTORY_VARS,
            "Import host vars",
            "Adds all inventory host variables as node's attributes",
            true,
            "false"
    );

    public static Property IGNORE_INVENTORY_VARS_PROP = PropertyUtil.string(
            ANSIBLE_IGNORE_INVENTORY_VARS,
            "Ignore host vars with this prefix",
            "List of prefixes. Variables starting with any of these won't be added as node's attribute",
            false,
            ""
    );

    static final Property EXTRA_VARS_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_EXTRA_VARS)
            .required(false)
            .title("Extra Variables")
            .description("Set additional playbook YAML or JSON variables.")
            .renderingOption(StringRenderingConstants.DISPLAY_TYPE_KEY, StringRenderingConstants.DisplayType.CODE)
            .renderingOption(StringRenderingConstants.CODE_SYNTAX_MODE, "yaml")
            .build();

    public static Property EXTRA_ATTRS_PROP = PropertyUtil.string(
            ANSIBLE_EXTRA_PARAM,
            "Extra Ansible arguments",
            "Additional ansible raw command line arguments to be appended to the executed command.",
            false,
            ""
      );

    static final Property VAULT_KEY_FILE_PROP = PropertyUtil.string(ANSIBLE_VAULT_PATH, "Vault Key File path",
            "File Path to the ansible vault Key to use",
            false, null);

    static final Property VAULT_KEY_STORAGE_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_VAULTSTORE_PATH)
            .required(false)
            .title("Vault Pass Storage Path")
            .description("Path to the Vault Key to use within Rundeck Storage. E.g. \"keys/path/ansible.vault\"")
            .renderingOption(StringRenderingConstants.SELECTION_ACCESSOR_KEY,
                    StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            .renderingOption(StringRenderingConstants.STORAGE_PATH_ROOT_KEY, "keys")
            .renderingOption(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-data-type=password")
            .build();

    static final Property VAULT_PASSWORD_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_VAULT_PASSWORD)
            .required(false)
            .title("Vault Password")
            .description("Ansible Vault password.")
            .renderingOption(StringRenderingConstants.DISPLAY_TYPE_KEY,
                    StringRenderingConstants.DisplayType.PASSWORD)
            .build();


    static final Property SSH_KEY_FILE_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_KEYPATH)
            .required(false)
            .title("SSH Key File path")
            .description("File Path to the SSH Key to use")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();


    static final Property SSH_KEY_STORAGE_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_KEYPATH_STORAGE_PATH)
            .required(false)
            .title("SSH Key Storage Path")
            .description("Path to the SSH Key to use within Rundeck Storage. E.g. \"keys/path/key1.pem\"")
            .renderingOption(StringRenderingConstants.SELECTION_ACCESSOR_KEY,
                    StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            .renderingOption(StringRenderingConstants.STORAGE_PATH_ROOT_KEY, "keys")
            .renderingOption(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-key-type=private")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_PASSWORD_STORAGE_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_PASSWORD_STORAGE_PATH)
            .required(false)
            .title("SSH Password Storage Path")
            .description("Path to the ssh Password to use within Rundeck Storage.")
            .renderingOption(StringRenderingConstants.SELECTION_ACCESSOR_KEY,
                    StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            .renderingOption(StringRenderingConstants.STORAGE_PATH_ROOT_KEY, "keys")
            .renderingOption(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-data-type=password")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_PASSWORD_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_PASSWORD)
            .required(false)
            .title("SSH Password")
            .description("Ansible SSH password.")
            .renderingOption(StringRenderingConstants.DISPLAY_TYPE_KEY,
                    StringRenderingConstants.DisplayType.PASSWORD)
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_AUTH_TYPE_PROP = PropertyBuilder.builder()
            .select(ANSIBLE_SSH_AUTH_TYPE)
            .required(false)
            .title("SSH Authentication")
            .description("Type of SSH Authentication to use.")
            .values(Arrays.asList(AuthenticationType.getValues()))
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_USER_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_USER)
            .required(false)
            .title("SSH User")
            .description("SSH User to authenticate as (default=rundeck).")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_TIMEOUT_PROP = PropertyBuilder.builder()
            .integer(ANSIBLE_SSH_TIMEOUT)
            .required(false)
            .title("SSH Timeout")
            .description("SSH timeout, override the SSH timeout in seconds (default=10).")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_USE_AGENT = PropertyBuilder.builder()
            .booleanType(ANSIBLE_SSH_USE_AGENT)
            .required(false)
            .title("Use ssh-agent.")
            .description("Use ssh-agent to connect with a private key plus a passphrase.")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_PASSPHRASE = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_PASSPHRASE)
            .required(false)
            .title("SSH Passphrase for private key")
            .description("SSH Passphrase for private key, it just works if the ssh-agent is used.")
            .renderingOption(StringRenderingConstants.SELECTION_ACCESSOR_KEY,
                    StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            .renderingOption(StringRenderingConstants.STORAGE_PATH_ROOT_KEY, "keys")
            .renderingOption(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-data-type=password")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();

    static final Property SSH_PASSPHRASE_OPTION = PropertyBuilder.builder()
            .string(ANSIBLE_SSH_PASSPHRASE_OPTION)
            .required(false)
            .title("SSH Passphrase from secure option")
            .description("SSH Passphrase from secure option on a job, it just works if the ssh-agent is used.")
            .defaultValue(DEFAULT_ANSIBLE_SSH_PASSPHRASE_OPTION)
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"SSH Connection")
            .build();




    static final Property BECOME_PROP = PropertyBuilder.builder()
            .booleanType(ANSIBLE_BECOME)
            .required(false)
            .title("Use become privilege escalation.")
            .description("Run operations with become (nopasswd implied).")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"Privilege Escalation")
            .build();

    static final Property BECOME_USER_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_BECOME_USER)
            .required(false)
            .title("Privilege escalation user")
            .description("run operations as this user (default=root).")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"Privilege Escalation")
            .build();


    static final Property BECOME_PASSWORD_STORAGE_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_BECOME_PASSWORD_STORAGE_PATH)
            .required(false)
            .title("Privilege escalation Password Storage Path.")
            .description("Become password used for privilege escalation.")
            .renderingOption(StringRenderingConstants.SELECTION_ACCESSOR_KEY,
                    StringRenderingConstants.SelectionAccessor.STORAGE_PATH)
            .renderingOption(StringRenderingConstants.STORAGE_PATH_ROOT_KEY, "keys")
            .renderingOption(StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY, "Rundeck-data-type=password")
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"Privilege Escalation")
            .build();

    static final Property BECOME_PASSWORD_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_BECOME_PASSWORD)
            .required(false)
            .title("Privilege escalation Password")
            .description("Become password used for privilege escalation.")
            .renderingOption(StringRenderingConstants.DISPLAY_TYPE_KEY,
                    StringRenderingConstants.DisplayType.PASSWORD)
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"Privilege Escalation")
            .build();

    static final Property BECOME_AUTH_TYPE_PROP = PropertyBuilder.builder()
            .select(ANSIBLE_BECOME_METHOD)
            .required(false)
            .title("Privilege escalation method.")
            .description("Privilege escalation method to use (default=sudo).")
            .values(Arrays.asList(BecomeMethodType.getValues()))
            .renderingOption(StringRenderingConstants.GROUPING,"SECONDARY")
            .renderingOption(StringRenderingConstants.GROUP_NAME,"Privilege Escalation")
            .build();

    static final Property CONFIG_FILE_PATH = PropertyBuilder.builder()
            .string(ANSIBLE_CONFIG_FILE_PATH)
            .required(false)
            .title("Ansible config file path")
            .description("Set ansible config file path.")
            .build();

    static final Property BINARIES_DIR_PATH_PROP = PropertyBuilder.builder()
            .string(ANSIBLE_BINARIES_DIR_PATH)
            .required(false)
            .title("Ansible binaries directory path")
            .description("Set ansible binaries directory path.")
            .build();

    public static final Property CONFIG_ENCRYPT_EXTRA_VARS = PropertyBuilder.builder()
            .booleanType(ANSIBLE_ENCRYPT_EXTRA_VARS)
            .required(false)
            .title("Encrypt Extra Vars.")
            .description("Encrypt the value of the extra vars keys.")
            .build();
}
