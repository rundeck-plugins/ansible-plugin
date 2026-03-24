[![Gitter](https://img.shields.io/gitter/room/rundeck-ansible-plugin/Lobby.svg)](https://gitter.im/rundeck-ansible-plugin/Lobby) [Read more about Rundeck + Ansible](https://www.rundeck.com/ansible)

Please [report](https://github.com/rundeck-plugins/ansible-plugin/issues) any errors or suggestions!

## **MIGRATION NOTICE!!** ##

In March of 2022 PagerDuty transferred this repo to the [Rundeck Plugins](https://github.com/rundeck-plugins) organization.  _frozenice_ has done a wonderful job bringing this functionality to the Rundeck Community and has asked Rundeck to take over maintenance going forward.  The move will allow us to better manage Issues and approve/merge Pull Requests, etc.  The plugin will continue to be available to the Open Source community.  Over the next few months our team will review all open PRs and merge or provide feedback/review comments as necessary.

## Rundeck Ansible Plugin ##

This plugin brings basic Ansible support to Rundeck. It imports hosts from Ansible's inventory, including a bunch of facts, and can run modules and playbooks. There is also a node executor and file copier for your project.

No SSH-Keys need to be shared between Ansible and Rundeck (but can be), everything is run through either `ansible` or `ansible-playbook` (even the node import).

**If you just want to give Rundeck and Ansible a quick try, check the [Docker container instructions](docker-container.md).**

## Components ##

The following bits are included:

### Resource Model Source ###

Uses the default configured inventory to scan for nodes. Facts are discovered by default, but you can turn that off (although I highly recommend leaving it on).

Host groups are imported as tags, you can limit the import to just some selected [patterns](http://docs.ansible.com/ansible/intro_patterns.html), if you want.

A bunch of facts are imported as attributes, e.g.:

![Example of node attributes being automatically set by Ansible facts](./node.png)

### Node Executor ###

This makes it possible to run commands via the "Commands" menu or the default "Command" node step in a job.

The command is passed to Ansible's `shell` module. You can specify which shell to use in the project settings.

### File Copier ###

Enables usage of the default "Copy File" and (in combination with the above) "Script" node steps.

Files are transferred using Ansible's `copy` module.

### Run Ansible Modules ###

Run any Ansible module! You can specify the module name and arguments.

### Run Ansible Playbooks ###

Run a playbook as a node or workflow step (see note above). You can specify either a path to a playbook file (which must be accessible to Rundeck), or write an inline playbook.

## Configuration ##

The Job Configuration, node, project and framework attributes can be used to customize how jobs are executed. On every run, the plugin will try to resolve
the value associated with each ansible configuration  by checking the configuration attributes in the following order:

* If the attribute is defined for the job
* else if the attribute is defined for the node (Only for node executor)
* else if the attribute is defined at the project level
* else if the attribute is defined at the framework level

Note that Node attributes are only evaluated for Node Executor jobs, Workflow Jobs (Playbook and Module) use only job configurations, and project/framework configurations.

The following configuration attributes can be set on the Node, or in the project.properties or framework.properties. To add them to project.properties, prefix them with "project." and for framework.properties prefix them with "framework.":

* `ansible-inventory` - Specifies the ansible inventory to use, can define a global inventory file at the project level without requiring setting the same variable for each job. It is also possible to provide an inventory _inline_ to a job. The default is /etc/ansible/hosts.
* `ansible-executable` - The executable to use for node Node Executor. (default /bin/sh)
* `ansible-base-dir-path` - Set ansible base directory path. This can be set project-wide to specify the working directory for Ansible operations.
* `ansible-limit` - Global groups limits can be set at the project level to filter hosts/groups from the Ansible inventory. See http://docs.ansible.com/ansible/intro_patterns.html for syntax help.
* `ansible-vault-path` - Default vault file path to use for Playbook Jobs.
* `ansible-vault-storage-path` - Specifies a [Key Storage Path][] to look up the ansible vault password from. If specified, it will be used instead of the `ansible-vault-path`.
* `ansible-ssh-auth-type` - Type of authentication to use, "password" or "privatekey", default: "privatekey".
* `ansible-ssh-user` - Ansible ssh User to user. (default rundeck)
* `ansible-ssh-password-option` - Specifies a [Secure Authentication Option][1] from a Job to use as the authentication password. (format: "NAME" ). This option take precedence over `ansible-ssh-password-storage-path`
	* default-value: "ansible-ssh-password", so simply define a Secure Authentication Option on your Job with the name "ansible-ssh-password".
* `ansible-ssh-password-storage-path` - Specifies a [Key Storage Path][] to look up the authentication password from.
* `ansible-ssh-timeout` - Ansible ssh timeout, default: 10.
* `ansible-ssh-keypath` - Specifies the path the ssh private key to use as the authentication privatekey.
* `ansible-ssh-key-storage-path` - Specifies a [Secure Authentication Option][1] from a Job to use as the authentication privatekey, This option take precedence over `ansible-ssh-keypath`.
* `ansible-become` - Specifies whether to use becaume or not for Ansible jobs and Node Executor, default: "false".
* `ansible-become-user` - Ansible default become user.
* `ansible-become-method` - Specifies the become method to use, "sudo" or "su", default: "sudo".
* `ansible-become-password-option` - Specifies a [Secure Authentication Option][1] from a Job to use for become. (format: "NAME" ). If specified, it will be used instead of the `ansible-become-password-storage-path`.
	* default-value: "ansible-become-password", so simply define a Secure Authentication Option on your Job with the name "ansible-become-password".
* `ansible-become-password-storage-path` - Specifies a [Key Storage Path][] to look up the become password from.

[Key Storage Path]: http://rundeck.org/docs/administration/key-storage.html

Password authentication can be performed in one of two ways:

1. Create a Rundeck Job with a [Secure Authentication Option][1], to pass in the password to use.  The default name of this option should be "ansible-ssh-password", but you can change the name that is expected, if necessary.
2. Use the Rundeck [Key Storage Facility][2] to store a password, and use the path to it as the `ansible-ssh-password-storage-path`
Note that the first takes precedence in evaluation over the second.

Private Key authentication can be performed by using a full path to the ssh private key (make sure the file is owned by rundeck and access permissions are set to 0600) or using [Key Storage Facility][2] to store a private key.

Become password configuration is very similar to ssh password, you can use either [Secure Authentication Option][1], the default option name should be "ansible-become-password" or use [Key Storage Facility][2] to store a password, and use the path to it as the `ansible-become-password-storage-path`. Also for become password just like ssh password the first takes precedence in evaluation over the second.  

[1]: http://rundeck.org/docs/manual/job-options.html#secure-options
[2]: http://rundeck.org/docs/administration/key-storage.html

## Requirements ##

- Ansible >= 1.7
- Ansible executables in `$PATH` of Rundeck user
- Rundeck user needs to be able to successfully run Ansible commands, that includes access to Ansible's config files and keys - it depends on your setup (whether you installed via .deb or launcher etc.)
  - You can check if everything works with something like this: `su rundeck -s /bin/bash -c "ansible all -m ping"`
  - If it complains, chances are that your rundeck `$HOME` directory isn't writable by Rundeck, fix it with e.g. `chown rundeck /var/lib/rundeck` (see [this issue](https://github.com/rundeck-plugins/ansible-plugin/issues/2#issuecomment-197000132))
  - Another thing, if you have a special setup: Rundeck's environment might be missing some things, if you are using `su` or similar to start rundeck - maybe you need to tell it to use a login shell via `-l` (see [this issue](https://github.com/rundeck-plugins/ansible-plugin/issues/3#issuecomment-198496564))
  - If you are running CentOS 6.7 or similar (RHEL) or another system using SELinux, you may need to install libselinux-python (`yum install libselinux-python`) or disable SELinux on boot (see [this issue](https://github.com/rundeck-plugins/ansible-plugin/issues/13))

## Installation ##

- [Download the .jar file from GitHub](https://github.com/rundeck-plugins/ansible-plugin/releases) or compile it yourself (using Gradle, either your own the included wrapper)
  - **Note**: This project now uses Gradle Version Catalogs for dependency management and includes security-hardened dependency versions
- Copy the .jar file to your Rundeck plugins directory (`/var/lib/rundeck/libext` if you installed the .deb, for example)
- Create a new project (this assumes you want every node in your project to be controlled via Ansible)
- Choose "Ansible Resource Model Source" as the resource model source
- Choose "Ansible Ad-Hoc Node Executor" as the default node executor
- Choose "Ansible File Copier" as the default node file copier
- Save, it can take a short time to import all the nodes, depending on your fleet
- You're all set! Try running a command

## Debugging ##

If anything goes wrong you can enable debugging for all components. Just enable the DEBUG log level for your jobs and add a Java system property named `ansible.debug` with the value `true`. You can do that for example in `/etc/rundeck/profile`, make sure to restart your rundeck service.

This will print extra info either in some logs (e.g. `/var/log/rundeck/service.log`) or the web console. If you file an issue, make sure to include as much information in your report as you can.

## Contributing ##

Discussions and pull requests are welcome.
