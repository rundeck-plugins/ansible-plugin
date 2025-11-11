package com.rundeck.plugins.ansible.ansible

import com.rundeck.plugins.ansible.util.ProcessExecutor
import spock.lang.Specification

import java.nio.file.Path

class AnsibleRunnerSpec extends Specification{

    def setup(){

    }

    def "wrong extra vars format"() {
        given:
        String playbook = "test"
        String privateKey = "privateKey"
        String extraVars = "123dxxx"

        def runner = AnsibleRunner.playbookInline(playbook)
        runner.encryptExtraVars(true)
        runner.sshPrivateKey(privateKey)
        runner.extraVars(extraVars)

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

        def ansibleVault = Mock(AnsibleVault){
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
        }


        runner.processExecutorBuilder(processBuilder)
        runner.ansibleVault(ansibleVault)
        runner.customTmpDirPath("/tmp")

        when:
        runner.build().run()

        then:
        def e = thrown(Exception)
        e.message.contains("cannot parse extra var values")

    }


    def "test encrypt extra vars"() {

        given:

        String playbook = "test"
        String privateKey = "privateKey"
        String extraVars = "test: 123\ntest2: 456"


        def runnerBuilder = AnsibleRunner.builder()
        runnerBuilder.type(AnsibleRunner.AnsibleCommand.PlaybookPath)
        runnerBuilder.playbook(playbook)
        runnerBuilder.encryptExtraVars(true)
        runnerBuilder.sshPrivateKey(privateKey)
        runnerBuilder.extraVars(extraVars)

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

        def ansibleVault = Mock(AnsibleVault){
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
        }

        runnerBuilder.processExecutorBuilder(processBuilder)
        runnerBuilder.ansibleVault(ansibleVault)
        runnerBuilder.customTmpDirPath("/tmp")

        when:
        def result = runnerBuilder.build().run()

        then:

        2 * ansibleVault.encryptVariable(_,_) >> "!vault | value"
        1* processBuilder.procArgs(_) >> { args ->
            def cmd = args[0]
            assert cmd.contains("--vault-id")
            assert cmd.contains("internal-encrypt@" + ansibleVault.getVaultPasswordScriptFile().absolutePath)
        }
        result == 0
    }


    def "test clean temporary directory"() {
        given:
        def tmpDirectory = File.createTempDir("ansible-runner-test-", "tmp")
        String playbook = "test"
        String privateKey = "privateKey"

        // IMPORTANT: At least one valid extra var so encryptVariable() is called
        String extraVars = "test: 123\ntest2: "  // test2 empty, test valid

        def runner = AnsibleRunner.playbookInline(playbook)
        runner.encryptExtraVars(true)
        runner.tempDirectory(Path.of(tmpDirectory.absolutePath))
        runner.sshPrivateKey(privateKey)
        runner.extraVars(extraVars)
        runner.customTmpDirPath("/tmp")

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        def processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder) {
            build() >> processExecutor
        }

        def ansibleVault = Mock(AnsibleVault) {
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
            encryptVariable(_ as String, _ as String) >> { throw new Exception("Error encrypting value") }  // should be called for "test"
        }

        runner.processExecutorBuilder(processBuilder)
        runner.ansibleVault(ansibleVault)

        when:
        runner.build().run()

        then:
        def e = thrown(Exception)
        e.message.contains("cannot encrypt extra var values")  // <--- updated message (your new function throws "cannot encrypt")

        !tmpDirectory.exists()
    }


    def "test clean temporary files when a exception is trigger"() {
        given:
        String playbook = "test"
        String privateKey = "privateKey"

        // IMPORTANT: At least one valid extra var so encryptVariable() is called
        String extraVars = "test: 123\ntest2: "  // test2 empty, test valid

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.encryptExtraVars(true)
        runnerBuilder.sshPrivateKey(privateKey)
        runnerBuilder.extraVars(extraVars)

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        def processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder) {
            build() >> processExecutor
        }

        def ansibleVault = Mock(AnsibleVault) {
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
            encryptVariable(_ as String, _ as String) >> { throw new Exception("Error encrypting value") }  // should be called for "test"
        }

        runnerBuilder.processExecutorBuilder(processBuilder)
        runnerBuilder.ansibleVault(ansibleVault)

        when:
        AnsibleRunner runner = runnerBuilder.build()
        runner.setCustomTmpDirPath("/tmp")
        runner.run()

        then:
        def e = thrown(Exception)
        e.message.contains("cannot encrypt extra var values")  // <--- updated message

        !runner.getTempPlaybook().exists()
    }


    def "test clean temporary files when process finished"(){
        given:

        String playbook = "test"
        String privateKey = "privateKey"
        String extraVars = "test: 123\ntest2: 456"

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.encryptExtraVars(true)
        runnerBuilder.sshPrivateKey(privateKey)
        runnerBuilder.extraVars(extraVars)

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


        def ansibleVault = Mock(AnsibleVault){
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
        }

        runnerBuilder.processExecutorBuilder(processBuilder)
        runnerBuilder.ansibleVault(ansibleVault)

        when:
        AnsibleRunner runner = runnerBuilder.build()
        runner.setCustomTmpDirPath("/tmp")
        def result = runner.run()

        then:
        2 * ansibleVault.encryptVariable(_,_) >> "!vault | value"
        result == 0
        !runner.getTempPlaybook().exists()
        !runner.getTempPkFile().exists()
        !runner.getTempVarsFile().exists()


    }

    def "test skip empty extra vars"() {
        given:
        String playbook = "test"
        String privateKey = "privateKey"
        String extraVars = "test: 123\nemptyVar: "  // emptyVar is empty → should be skipped

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.encryptExtraVars(true)
        runnerBuilder.sshPrivateKey(privateKey)
        runnerBuilder.extraVars(extraVars)

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        def processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder) {
            build() >> processExecutor
        }

        def ansibleVault = Mock(AnsibleVault) {
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
        }

        runnerBuilder.processExecutorBuilder(processBuilder)
        runnerBuilder.ansibleVault(ansibleVault)
        runnerBuilder.customTmpDirPath("/tmp")

        when:
        AnsibleRunner runner = runnerBuilder.build()
        def result = runner.run()

        then:
        // Only 1 encryptVariable call — "test" — "emptyVar" is skipped
        1 * ansibleVault.encryptVariable(_,_) >> "!vault | value"
        result == 0
        !runner.getTempPlaybook().exists()
        !runner.getTempVarsFile().exists()
    }


    def "test password authentication with encrypted extra vars "(){
        given:

        String playbook = "test"

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.sshPass("123456")
        runnerBuilder.sshUser("user")
        runnerBuilder.sshUsePassword(true)

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


        def ansibleVault = Mock(AnsibleVault){
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
        }

        runnerBuilder.processExecutorBuilder(processBuilder)
        runnerBuilder.ansibleVault(ansibleVault)

        when:
        AnsibleRunner runner = runnerBuilder.build()
        runner.setCustomTmpDirPath("/tmp")
        def result = runner.run()

        then:
        1 * ansibleVault.encryptVariable(_,_) >> "!vault | value"
        result == 0
        !runner.getTempPlaybook().exists()
        !runner.getTempSshVarsFile().exists()
    }

    def "test password authentication and became user with encrypted extra vars "(){
        given:

        String playbook = "test"

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.sshPass("123456")
        runnerBuilder.sshUser("user")
        runnerBuilder.sshUsePassword(true)
        runnerBuilder.become(true)
        runnerBuilder.becomeUser("root")
        runnerBuilder.becomePassword("123")
        runnerBuilder.becomeMethod("sudo")

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


        def ansibleVault = Mock(AnsibleVault){
            checkAnsibleVault() >> true
            getVaultPasswordScriptFile() >> new File("vault-script-client.py")
        }

        runnerBuilder.processExecutorBuilder(processBuilder)
        runnerBuilder.ansibleVault(ansibleVault)

        when:
        AnsibleRunner runner = runnerBuilder.build()
        runner.setCustomTmpDirPath("/tmp")
        def result = runner.run()

        then:
        2 * ansibleVault.encryptVariable(_,_) >> "!vault | value"
        result == 0
        !runner.getTempPlaybook().exists()
        !runner.getTempSshVarsFile().exists()
        !runner.getTempBecameVarsFile().exists()
    }
    def "adhoc: uses -i, no -t, and sets callback envs"() {
        given:
        def runnerBuilder = AnsibleRunner.adHoc("ansible.builtin.ping", null)
                .inventory("/tmp/rdk-inv.ini")
                .limits("target1")
                .customTmpDirPath("/tmp")

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream(new byte[0])
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream(new byte[0])
            destroy() >> { }
        }
        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        List capturedArgs = null
        Map<String,String> capturedEnv = null

        ProcessExecutor.ProcessExecutorBuilder processBuilder =
                Mock(ProcessExecutor.ProcessExecutorBuilder)

        // keep fluent chain by always returning the builder
        processBuilder.build() >> processExecutor
        processBuilder.procArgs(_ as List) >> { List a -> capturedArgs = new ArrayList(a); return processBuilder }
        processBuilder.environmentVariables(_ as Map<String,String>) >> { Map<String,String> e -> capturedEnv = new HashMap<>(e); return processBuilder }
        processBuilder.baseDirectory(_ as File) >> { File f -> return processBuilder }
        processBuilder.stdinVariables(_ as List) >> { List v -> return processBuilder }
        processBuilder.promptStdinLogFile(_ as File) >> { File f -> return processBuilder }
        processBuilder.debug(_ as boolean) >> { boolean d -> return processBuilder }

        runnerBuilder.processExecutorBuilder(processBuilder)

        when:
        def rc = runnerBuilder.build().run()

        then:
        rc == 0

        // --- inventory & deprecation checks (flattened, robust) ---
        def invPath = "/tmp/rdk-inv.ini"
        assert capturedArgs != null : "proc args were null"

        // Flatten in case the mock handed us a nested list (e.g., [[...]])
        def flatArgs = capturedArgs.flatten().collect { it?.toString() }
        println "Captured procArgs (flat): ${flatArgs}"

        // preferred form: -i <path>
        int iPos = flatArgs.indexOf("-i")
        boolean hasDashISeparate = (iPos >= 0 && iPos + 1 < flatArgs.size() && flatArgs[iPos + 1] == invPath)

        // defensive: -i=<path> or -i<path>
        boolean hasDashIEquals = flatArgs.any { it == "-i=${invPath}" || it == "-i${invPath}" }

        // must not use deprecated long flag (covers "--inventory-file" and "--inventory-file=/path")
        boolean hasDeprecatedLong =
                flatArgs.contains("--inventory-file") ||
                        flatArgs.any { it.startsWith("--inventory-file=") }

        assert (hasDashISeparate || hasDashIEquals) :
                "Expected -i ${invPath} (or -i=${invPath}) in args, but got: ${flatArgs}"
        assert !hasDeprecatedLong : "Found deprecated --inventory-file in args: ${flatArgs}"

        // also ensure no '-t'
        assert !flatArgs.contains("-t")

        // env for ad-hoc tree replacement
        assert capturedEnv != null : "env was null"
        capturedEnv.get("ANSIBLE_LOAD_CALLBACK_PLUGINS") == "1"
        capturedEnv.get("ANSIBLE_CALLBACKS_ENABLED") == "ansible.builtin.tree"
        capturedEnv.get("ANSIBLE_CALLBACK_TREE_DIR") != null
    }

    def "playbook path: uses -i but does NOT set ad-hoc callback envs"() {
        given:
        def runnerBuilder = AnsibleRunner.playbookPath("/tmp/playbook.yml")
                .inventory("/tmp/rdk-inv.ini")
                .customTmpDirPath("/tmp")

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream(new byte[0])
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream(new byte[0])
            destroy() >> { }
        }
        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        List capturedArgs = null
        Map<String,String> capturedEnv = null

        def processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder)
        processBuilder.build() >> processExecutor
        processBuilder.procArgs(_ as List) >> { List a -> capturedArgs = new ArrayList(a); return processBuilder }
        processBuilder.environmentVariables(_ as Map<String,String>) >> { Map<String,String> e -> capturedEnv = new HashMap<>(e); return processBuilder }
        processBuilder.baseDirectory(_ as File) >> { File f -> return processBuilder }
        processBuilder.stdinVariables(_ as List) >> { List v -> return processBuilder }
        processBuilder.promptStdinLogFile(_ as File) >> { File f -> return processBuilder }
        processBuilder.debug(_ as boolean) >> { boolean d -> return processBuilder }

        runnerBuilder.processExecutorBuilder(processBuilder)

        when:
        def rc = runnerBuilder.build().run()

        then:
        rc == 0
        assert capturedArgs != null : "proc args were null"

        // Flatten & stringify to avoid type surprises (and nested lists)
        def flatArgs = capturedArgs.flatten().collect { it?.toString() }
        println "Captured procArgs (flat): ${flatArgs}"

        // inventory via -i (or -i=)
        def invPath = "/tmp/rdk-inv.ini"
        int iPos = flatArgs.indexOf("-i")
        boolean hasDashISeparate = (iPos >= 0 && iPos + 1 < flatArgs.size() && flatArgs[iPos + 1] == invPath)
        boolean hasDashIEquals   = flatArgs.any { it == "-i=${invPath}" || it == "-i${invPath}" }

        // must not use deprecated long flag
        boolean hasDeprecatedLong =
                flatArgs.contains("--inventory-file") ||
                        flatArgs.any { it.startsWith("--inventory-file=") }

        assert (hasDashISeparate || hasDashIEquals) :
                "Expected -i ${invPath} (or -i=${invPath}) in args, but got: ${flatArgs}"
        assert !hasDeprecatedLong : "Found deprecated --inventory-file in args: ${flatArgs}"

        // playbook path should NOT set the ad-hoc callback envs
        assert capturedEnv != null : "env was null"
        capturedEnv.get("ANSIBLE_LOAD_CALLBACK_PLUGINS") == null
        capturedEnv.get("ANSIBLE_CALLBACKS_ENABLED") == null
        capturedEnv.get("ANSIBLE_CALLBACK_TREE_DIR") == null
    }


    def "adhoc: respects user-provided callback envs (putIfAbsent)"() {
        given:
        def runnerBuilder = AnsibleRunner.adHoc("ansible.builtin.ping", null)
                .inventory("/tmp/rdk-inv.ini")
                .limits("target1")
                .options([
                        "ANSIBLE_CALLBACKS_ENABLED": "custom.callback",
                        "ANSIBLE_LOAD_CALLBACK_PLUGINS": "0",
                        "ANSIBLE_CALLBACK_TREE_DIR": "/tmp/custom-tree"
                ])
                .customTmpDirPath("/tmp")

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream(new byte[0])
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream(new byte[0])
            destroy() >> { }
        }
        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        Map<String,String> capturedEnv = null

        def processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder)
        processBuilder.build() >> processExecutor
        processBuilder.procArgs(_ as List<String>) >> { List<String> a -> return processBuilder }
        processBuilder.environmentVariables(_ as Map<String,String>) >> { Map<String,String> e -> capturedEnv = new HashMap<>(e); return processBuilder }
        processBuilder.baseDirectory(_ as File) >> { File f -> return processBuilder }
        processBuilder.stdinVariables(_ as List) >> { List v -> return processBuilder }
        processBuilder.promptStdinLogFile(_ as File) >> { File f -> return processBuilder }
        processBuilder.debug(_ as boolean) >> { boolean d -> return processBuilder }

        runnerBuilder.processExecutorBuilder(processBuilder)

        when:
        def rc = runnerBuilder.build().run()

        then:
        rc == 0
        assert capturedEnv != null : "env was null"
        // user-provided values win because putIfAbsent was used
        capturedEnv.get("ANSIBLE_CALLBACKS_ENABLED") == "custom.callback"
        capturedEnv.get("ANSIBLE_LOAD_CALLBACK_PLUGINS") == "0"
        capturedEnv.get("ANSIBLE_CALLBACK_TREE_DIR") == "/tmp/custom-tree"
    }


}
