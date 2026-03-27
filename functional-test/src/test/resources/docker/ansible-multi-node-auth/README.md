# Multi-Node Authentication Functional Test

This test verifies the multi-node authentication feature where each node can have its own credentials (password or private key) stored in Rundeck's key storage.

## Test Setup

### Nodes
- **ssh-node**: Standard password (`testpassword123`)
- **ssh-node-2**: Password with special characters (`password2_special!@#`)
- **ssh-node-3**: Password containing both a double quote (") and a single quote ('): password3"quote'test
- **ssh-node-4**: Private key authentication (uses SSH key from key storage)

### Files
- `resources.xml`: Defines four nodes with node-specific authentication (three with password storage paths, one with private key storage path)
- `inventory.ini`: Ansible inventory file for the test nodes
- `ansible.cfg`: Ansible configuration
- `test-playbook.yml`: Test playbook for verification

## What's Being Tested

1. **Multi-node authentication with different credentials per node**
   - Three nodes have different passwords stored in Rundeck key storage
   - One node uses private key authentication stored in Rundeck key storage
   - The plugin generates `group_vars/all.yaml` with vault-encrypted passwords and private key paths
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
     ssh-node-4: rundeck
   host_private_keys:
     ssh-node-4: /path/to/temporary/key
   ```
6. Ansible uses host-specific credentials from group_vars
7. Each node authenticates successfully with its own credentials (password or private key)
