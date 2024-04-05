package com.rundeck.plugins.ansible.ansible

import com.rundeck.plugins.ansible.util.ProcessExecutor
import spock.lang.Specification

import java.nio.file.Path

class AnsibleVaultSpec extends Specification{

    def "prompt message not found finished with timeout"() {
        given:

        def process = Mock(Process){
            waitFor() >> 0
            getInputStream()>> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor){
            run()>>process
        }

        def processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder){
            build() >> processExecutor
        }

        File passwordScript = File.createTempFile("password", ".python")
        Path baseDirectory = Path.of(passwordScript.getParentFile().getPath())

        when:
        def vault = AnsibleVault.builder()
                .processExecutorBuilder(processBuilder)
                .vaultPasswordScriptFile(passwordScript)
                .baseDirectory(baseDirectory)
                .build()

        def key = "password"
        def content = "1234"
        def result = vault.encryptVariable(key, content)

        then:
        1* processBuilder.procArgs(_) >> processBuilder
        1* processBuilder.baseDirectory(_) >> processBuilder
        1* processBuilder.environmentVariables(_) >> processBuilder
        1* processBuilder.redirectErrorStream(_) >> processBuilder

        def e = thrown(RuntimeException)
        e.message.contains("Failed to find prompt for ansible-vault")
    }

}
