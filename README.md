**This is an alpha release!** Use with caution.

Please [report](https://github.com/Batix/rundeck-ansible-plugin/issues) any errors or suggestions!

## Rundeck Ansible Plugin ##

This plugin brings basic Ansible support to Rundeck. It imports hosts from Ansible's inventory, including a bunch of facts, and can run modules and playbooks. There is also a node executor and file copier for your project.

No SSH-Keys need to be shared between Ansible and Rundeck, everything is run through either `ansible` or `ansible-playbook` (even the node import).

The following bits are included:

### Resource Model Source ###

Uses the default configured inventory to scan for nodes. Facts are discovered by default, but you can turn that off (although I highly recommend leaving it on).

Host groups are imported as tags, you can limit the import to just some selected [patterns](http://docs.ansible.com/ansible/intro_patterns.html), if you want.

A bunch of facts are imported as attributes ([sample screenshot](http://batix.de/static/files/rundeck-ansible/node.png)).

### Node Executor ###

This makes it possible to run commands via the "Commands" menu or the default "Command" node step in a job.

The command is passed to Ansible's `shell` module. You can specify which shell to use in the project settings.

### File Copier ###

Enables usage of the default "Copy File" and (in combination with the above) "Script" node steps.

Files are transferred using Ansible's `copy` module.

### Run Ansible Modules ###

Run any Ansible module! You can specify the module name and arguments.

This is available as both a node and workflow step.

Note: The node step runs Ansible for every node, targeting only one node. The workflow step runs Ansible only once with a list of targets, so it should perform a bit better, if you don't need the individuality.

### Run Ansible Playbooks ###

Run a playbook as a node or workflow step (see note above). You specify a path to a file, which must be accessible to Rundeck.

## Requirements ##

- Ansible executables in `$PATH` of Rundeck user
- Rundeck user needs to be able to successfully run Ansible commands, that includes access to Ansible's config files and keys - it depends on your setup (whether you installed via .deb or launcher etc.)
  - You can check if everything works with something like this: `su rundeck -s /bin/bash -c "ansible all -m ping"`
  - If it complains, chances are that your rundeck `$HOME` directory isn't writable by Rundeck, fix it with e.g. `chown rundeck /var/lib/rundeck` (see [this issue](https://github.com/Batix/rundeck-ansible-plugin/issues/2#issuecomment-197000132))
  - Another thing, if you have a special setup: Rundeck's environment might be missing some things, if you are using `su` or similar to start rundeck - maybe you need to tell it to use a login shell via `-l` (see [this issue](https://github.com/Batix/rundeck-ansible-plugin/issues/3#issuecomment-198496564))
  - If you are running CentOS 6.7 or similar (RHEL) or another system using SELinux, you may need to install libselinux-python (`yum install libselinux-python`) or disable SELinux on boot (see [this issue](https://github.com/Batix/rundeck-ansible-plugin/issues/13))

## Installation ##

- [Download the .jar file from GitHub](https://github.com/Batix/rundeck-ansible-plugin/releases) or compile it yourself (using Gradle, either your own the included wrapper)
- Copy the .jar file to your Rundeck plugins directory (`/var/lib/rundeck/libext` if you installed the .deb, for example)
- Create a new project (this assumes you want every node in your project to be controlled via Ansible)
- Choose "Ansible Resource Model Source" as the resource model source
- Choose "Ansible Ad-Hoc Node Executor" as the default node executor
- Choose "Ansible File Copier" as the default node file copier
- Save, it can take a short time to import all the nodes, depending on your fleet
- You're all set! Try running a command

## Notes ##
I'm new to both Rundeck and Ansible so I expect there to be room for improvements. Only basic features have been implemented in this first pass, so I can play around with both tools. Liking it very much so far! :)

## Docker ##
The docker image, available on dockerhub as giovannipaologibilisco/rundeck-ansible-plugin allows to easily run ansible and rundeck. 
The installation of rundeck includes the plugin developed in this repo for the intergation with Ansible. 
To run the container specify:
 - the rundeck port to expose (-p 4440:4440)
 - a volume containing the required configuration ( -v CONFIG_FOLDER:/etc/settings/ansible)

The CONFIG_FOLDER should contain:
 - id_rsa -> private key used by ansible to connect to hosts via SSH
 - hosts -> ansible configuration file with the groups of hosts to control (a static ip should be provded e.g. ansible_host=192.168.100.120)
 - ansible.cfg -> the main ansible configuration file (optional)

Example of running the image: docker run -p 4440:4440 -v /home/giovanni/ansible-configuration:/etc/settings/ansible giovannipaologibilisco/rundeck-ansible-plugin
