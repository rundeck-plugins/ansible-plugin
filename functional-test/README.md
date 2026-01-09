# Functional Tests for Ansible Plugin

This directory contains functional tests for the Rundeck Ansible plugin using Testcontainers and Docker.

## Prerequisites

- Docker (Docker Desktop or Rancher Desktop)
- Java 11 or later
- Gradle 7.2 or later

## Docker Configuration

The functional tests use Testcontainers to spin up Docker containers for Rundeck and SSH nodes. Depending on your Docker setup, you may need to configure the Docker socket path.

### Option 1: Using ~/.testcontainers.properties (Recommended)

Create a file at `~/.testcontainers.properties` with the following content:

**For Rancher Desktop on macOS:**
```properties
docker.host=unix:///Users/<your-username>/.rd/docker.sock
```

**For Docker Desktop on macOS/Linux:**
```properties
docker.host=unix:///var/run/docker.sock
```

**For Windows with Docker Desktop:**
```properties
docker.host=tcp://localhost:2375
```

### Option 2: Modify build.gradle

Edit `functional-test/build.gradle` and update the `docker.host` paths on lines 52-53:

```gradle
systemProperty('docker.host', "unix:///path/to/your/docker.sock")
environment 'DOCKER_HOST', 'unix:///path/to/your/docker.sock'
```

## Running the Tests

### Run All Functional Tests

```bash
./gradlew :functional-test:functionalTest
```

### Run Specific Test Suite

```bash
# Multi-node authentication tests
./gradlew :functional-test:functionalTest --tests "*MultiNodeAuthSpec*"

# Basic integration tests
./gradlew :functional-test:functionalTest --tests "*BasicIntegrationSpec*"

# Plugin group tests
./gradlew :functional-test:functionalTest --tests "*PluginGroupIntegrationSpec*"
```

## Test Suites

### MultiNodeAuthSpec
Tests the multi-node authentication feature where each node can have its own password stored in Rundeck's key storage.

**Tests:**
- `test ansible playbook with multi-node authentication` - Verifies Ansible playbooks execute across multiple nodes with per-node credentials
- `test multi-node authentication with different passwords` - Tests script execution on multiple nodes with different passwords
- `test nodes are accessible with different credentials` - Validates node registration and discovery
- `test passwords with special characters are properly escaped` - Verifies YAML escaping for special characters in passwords

**Test Environment:**
- 3 SSH nodes (ssh-node, ssh-node-2, ssh-node-3)
- Each node has a different password, including special characters
- Tests both WorkflowStep (Ansible playbooks) and NodeStep (scripts) execution

### BasicIntegrationSpec
Basic integration tests for the Ansible plugin functionality.

### PluginGroupIntegrationSpec
Tests for plugin group configuration and execution.

## Test Structure

```
functional-test/
├── build.gradle                          # Test configuration
├── README.md                             # This file
└── src/
    └── test/
        ├── groovy/functional/            # Test specifications
        │   ├── MultiNodeAuthSpec.groovy
        │   ├── BasicIntegrationSpec.groovy
        │   └── PluginGroupIntegrationSpec.groovy
        └── resources/
            ├── docker/                   # Docker compose and configs
            │   ├── docker-compose.yml
            │   ├── ansible-multi-node-auth/  # Multi-node test configs
            │   ├── keys/                     # SSH keys for tests
            │   ├── node/                     # SSH node Docker configs
            │   └── rundeck/                  # Rundeck Docker configs
            └── project-import/           # Rundeck project definitions
                └── ansible-multi-node-auth/
                    └── rundeck-ansible-multi-node-auth/
                        ├── files/etc/project.properties
                        └── jobs/*.xml
```

## Troubleshooting

### Tests Fail with "Could not find Docker socket"

**Problem:** Testcontainers cannot locate the Docker socket.

**Solution:**
1. Verify Docker is running: `docker ps`
2. Check your Docker socket path:
   - Rancher Desktop: `ls -la ~/.rd/docker.sock`
   - Docker Desktop: `ls -la /var/run/docker.sock`
3. Update `~/.testcontainers.properties` or `build.gradle` with the correct path

### Tests Timeout or Hang

**Problem:** Tests take too long or appear to hang.

**Solution:**
1. Check Docker container status: `docker ps -a`
2. Check Docker logs: `docker logs <container-id>`
3. Increase test timeout in build.gradle if needed
4. Ensure sufficient Docker resources (memory/CPU)

### Port Conflicts

**Problem:** Tests fail with "port already in use" errors.

**Solution:**
1. Check for running containers: `docker ps`
2. Stop conflicting containers: `docker stop <container-name>`
3. Clean up: `docker-compose down` in the docker directory

### Platform Mismatch Warnings (Apple Silicon)

**Problem:** Warnings about platform mismatch (linux/amd64 vs linux/arm64).

**Solution:** These warnings are expected on Apple Silicon Macs and can be safely ignored. Docker will use Rosetta 2 for emulation.

## Test Reports

After running tests, view the HTML report at:
```
functional-test/build/reports/tests/functionalTest/index.html
```

## Adding New Tests

1. Create a new Spock specification in `src/test/groovy/functional/`
2. Extend `BaseTestConfiguration` for common test utilities
3. Add any required Docker configs to `src/test/resources/docker/`
4. Add project imports to `src/test/resources/project-import/`
5. Follow existing test patterns for consistency

## Multi-Node Authentication Feature

The multi-node authentication feature allows running Ansible playbooks across multiple nodes where each node has its own password stored in Rundeck's key storage.

**How it works:**
1. Enable at project level: `project.ansible-generate-inventory-nodes-auth=true`
2. Store per-node passwords in Key Storage with paths specified in node attributes
3. Plugin generates `group_vars/all.yaml` with vault-encrypted passwords
4. Ansible uses host-specific credentials from group_vars

**Requirements:**
- Must use Ansible Playbook **Workflow Steps** (not Node Steps)
- Node attributes must include `ansible-ssh-password-storage-path` for each node
- Passwords are automatically encrypted using Ansible Vault
- Supports special characters with proper YAML escaping

See `MultiNodeAuthSpec.groovy` for comprehensive test examples.
