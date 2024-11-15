package com.rundeck.plugins.ansible.ansible;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.rundeck.plugins.ansible.util.AnsibleUtil;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;

public class AnsibleInlineInventoryBuilder {

    private final String inline_inventory;

    public AnsibleInlineInventoryBuilder(String inline_inventory) {
        this.inline_inventory = inline_inventory;
    }

    public File buildInventory() throws ConfigurationException {
        try {
            File file = AnsibleUtil.createTemporaryFile("ansible-inventory", ".inventory","");
            file.deleteOnExit();
            PrintWriter writer = new PrintWriter(file);
            writer.write(inline_inventory);
            writer.close();
            return file;
        } catch (Exception e) {
            throw new ConfigurationException("Could not write temporary inventory: " + e.getMessage());
        }
    }
}
