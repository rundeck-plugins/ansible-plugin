package com.rundeck.plugins.ansible.util;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class VaultPrompt {

    private String vaultId;
    private String vaultPassword;

}
