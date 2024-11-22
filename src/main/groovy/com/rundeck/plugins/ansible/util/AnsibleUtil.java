package com.rundeck.plugins.ansible.util;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.proxy.DefaultSecretBundle;
import com.dtolabs.rundeck.core.execution.proxy.SecretBundle;
import com.dtolabs.rundeck.core.execution.workflow.steps.PluginStepContextImpl;
import com.dtolabs.rundeck.core.plugins.configuration.Property;
import com.dtolabs.rundeck.core.utils.PropertyLookup;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.rundeck.plugins.ansible.ansible.AnsibleDescribable;
import com.rundeck.plugins.ansible.ansible.AnsibleRunnerContextBuilder;
import com.rundeck.plugins.ansible.plugin.AnsibleNodeExecutor;

import java.io.*;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public class AnsibleUtil {

    public static SecretBundle createBundle(AnsibleRunnerContextBuilder builder){

        DefaultSecretBundle secretBundle = new DefaultSecretBundle();

        try {
            if(builder.getPasswordStoragePath()!=null){
                secretBundle.addSecret(
                        builder.getPasswordStoragePath(),
                        builder.getPasswordStorageData()
                );
            }

            if(builder.getPrivateKeyStoragePath()!=null){
                secretBundle.addSecret(
                        builder.getPrivateKeyStoragePath(),
                        builder.getPrivateKeyStorageDataBytes()
                );
            }

            if(builder.getBecomePasswordStoragePath()!=null){
                secretBundle.addSecret(
                        builder.getBecomePasswordStoragePath(),
                        builder.getBecomePasswordStorageData()
                );
            }

            return secretBundle;

        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getSecretsPath(AnsibleRunnerContextBuilder builder){
        List<String> secretPaths = new ArrayList<>();
        if(builder.getPasswordStoragePath()!=null){
            secretPaths.add(
                    builder.getPasswordStoragePath()
            );
        }

        if(builder.getPrivateKeyStoragePath()!=null){
            secretPaths.add(
                    builder.getPrivateKeyStoragePath()
            );
        }

        if(builder.getBecomePasswordStoragePath()!=null){
            secretPaths.add(
                    builder.getBecomePasswordStoragePath()
            );
        }
        if(builder.getPassphraseStoragePath()!=null){
            secretPaths.add(
                    builder.getPassphraseStoragePath()
            );
        }
        if(builder.getVaultKeyStoragePath()!=null){
            secretPaths.add(
                    builder.getVaultKeyStoragePath()
            );
        }
        return secretPaths;

    }

    public static Map<String, String> getRuntimeProperties(ExecutionContext context, String propertyPrefix) {
        Map<String, String> properties = null;

        if(propertyPrefix.equals(AnsibleDescribable.PROJ_PROP_PREFIX)){
            properties = context.getIFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()).getProjectProperties();
        }else{
            properties = context.getIFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()).getProperties();
        }

        List<String> nodeExecutorProperties = AnsibleNodeExecutor.DESC.getProperties().stream().map(Property::getName).collect(Collectors.toList());
        Map<String, String> filterProperties = new HashMap<>();

        properties.forEach((key,value) -> {
            String propertyName = key.replace(propertyPrefix, "");
            if (key.startsWith(propertyPrefix) && nodeExecutorProperties.contains(propertyName)) {
                filterProperties.put(key, value);
            }
        });
        return filterProperties;
    }

    public static File createTemporaryFile(String prefix, String suffix, String data, String path) throws IOException {
        if(prefix.isEmpty()){
            prefix ="ansible-runner";
        }
        File tempVarsFile = File.createTempFile(prefix, suffix, new File(path));
        Files.write(tempVarsFile.toPath(), data.getBytes());
        return tempVarsFile;
    }

    public static String randomString(){
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String getCustomTmpPathDir(Framework framework){
        String customTmpDir = framework.getPropertyLookup().getProperty("framework.tmp.dir");
        if (customTmpDir.isEmpty()) {
            customTmpDir = System.getProperty("java.io.tmpdir");
        }
        return  customTmpDir;
    }


}
