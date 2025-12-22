package com.rundeck.plugins.ansible.util;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Custom YAML constructor that extends SafeConstructor to handle Ansible's !vault tag.
 *
 * This constructor allows SnakeYAML to parse Ansible inventory files that contain
 * vault-encrypted variables without throwing a YAMLException.
 *
 * When a !vault tag is encountered, it treats the encrypted value as an opaque string,
 * allowing the YAML parsing to succeed. The encrypted value is preserved as-is in the
 * resulting data structure.
 *
 * Example vault format in Ansible inventory:
 * my_password: !vault |
 *   $ANSIBLE_VAULT;1.1;AES256
 *   666f6f0a...
 *
 * @see <a href="https://github.com/rundeck-plugins/ansible-plugin/issues/385">GitHub Issue #385</a>
 */
public class VaultAwareConstructor extends SafeConstructor {

    /**
     * Constructs a VaultAwareConstructor with the specified loader options.
     * Registers a custom constructor for the !vault tag.
     *
     * @param loadingConfig the loader options to use
     */
    public VaultAwareConstructor(LoaderOptions loadingConfig) {
        super(loadingConfig);
        // Register the !vault tag handler
        this.yamlConstructors.put(new Tag("!vault"), new ConstructVaultString());
    }

    /**
     * Constructor for handling !vault tagged values.
     * Treats the vault-encrypted content as a regular string without attempting to decrypt it.
     */
    private class ConstructVaultString extends AbstractConstruct {
        @Override
        public Object construct(Node node) {
            if (node instanceof ScalarNode) {
                // Return the encrypted vault string as-is
                // This preserves the encrypted value without attempting decryption
                return constructScalar((ScalarNode) node);
            }
            // Fallback: return empty string if the node structure is unexpected
            return "";
        }
    }
}
