# Multi-Node Authentication Functional Test

This test verifies the multi-node authentication feature where each node can have its own password stored in Rundeck's key storage.

## Test Setup

### Nodes
- **ssh-node**: Standard password (`testpassword123`)
- **ssh-node-2**: Password with special characters (`password2_special!@#`)
- **ssh-node-3**: Password with quotes (`password3"quote'test`)

### Files
- `resources.xml`: Defines three nodes with node-specific password storage paths
- `inventory.ini`: Ansible inventory file for the test nodes
- `ansible.cfg`: Ansible configuration
- `test-playbook.yml`: Test playbook for verification

## What's Being Tested

1. **Multi-node authentication with different passwords per node**
   - Each node has a different password stored in Rundeck key storage
   - The plugin generates `group_vars/all.yaml` with vault-encrypted passwords
   - Each node authenticates with its specific credentials

2. **Password escaping for special characters**
   - Node 2 has special characters: `!`, `@`, `#`
   - Node 3 has quotes: `"` and `'`
   - Tests verify YAML escaping works correctly

3. **Project-level configuration**
   - `project.ansible-generate-inventory-nodes-auth=true` enables the feature
   - Configuration is at project level, not workflow step level

## Jobs

- **multi-node-ping-test**: Simple script that runs on all nodes
- **ansible-playbook-multi-node-test**: Ansible playbook that verifies authentication on each node

## Expected Behavior

1. Plugin reads node attributes to get password storage paths for each node
2. Plugin retrieves passwords from Rundeck key storage
3. Plugin escapes passwords to prevent YAML parsing errors
4. Plugin encrypts passwords using Ansible Vault
5. Plugin generates `group_vars/all.yaml` with:
   ```yaml
   host_passwords:
     ssh-node: !vault | ...
     ssh-node-2: !vault | ...
     ssh-node-3: !vault | ...
   host_users:
     ssh-node: rundeck
     ssh-node-2: rundeck
     ssh-node-3: rundeck
   ```
6. Ansible uses host-specific credentials from group_vars
7. Each node authenticates successfully with its own password
