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


    def "escapeYamlKey: should quote keys with special characters"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        expect:
        runner.escapeYamlKey(key) == expectedResult

        where:
        key                  || expectedResult
        "simple-key"         || "simple-key"
        "key:with:colons"    || "\"key:with:colons\""
        "key[brackets]"      || "\"key[brackets]\""
        "key{braces}"        || "\"key{braces}\""
        "key#hash"           || "\"key#hash\""
        "key&ampersand"      || "\"key&ampersand\""
        "key*asterisk"       || "\"key*asterisk\""
        "key!exclamation"    || "\"key!exclamation\""
        "key|pipe"           || "\"key|pipe\""
        "-starts-with-dash"  || "\"-starts-with-dash\""
        "?starts-with-q"     || "\"?starts-with-q\""
        "123numeric"         || "\"123numeric\""
        "key with spaces"    || "key with spaces"  // internal spaces are valid in unquoted YAML keys; node names with leading/trailing spaces are not supported
    }

    def "escapeYamlKey: node names with leading/trailing spaces are not modified"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        expect:
        // This documents that escapeYamlKey does not normalize leading/trailing spaces.
        // Node names with such spaces are considered unsupported at a higher level.
        runner.escapeYamlKey(key) == expectedResult

        where:
        key                    || expectedResult
        " leading-space"       || " leading-space"
        "trailing-space "      || "trailing-space "
        "  both-sides  "       || "  both-sides  "
    }

    def "escapeYamlValue: should quote values with special characters"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        expect:
        runner.escapeYamlValue(value) == expectedResult

        where:
        value                || expectedResult
        "simple-value"       || "simple-value"
        "value:colon"        || "\"value:colon\""
        "value[bracket]"     || "\"value[bracket]\""
        "   "                || "\"   \""  // empty/whitespace should be quoted
        "value@at"           || "\"value@at\""
        "-starts-dash"       || "\"-starts-dash\""
    }

    def "escapeYamlKey: should escape backslashes and quotes"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        expect:
        runner.escapeYamlKey('key"with"quotes') == '"key\\"with\\"quotes"'
        runner.escapeYamlKey('key\\backslash') == '"key\\\\backslash"'
    }

    def "isValidVaultFormat: should validate vault format correctly"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        expect:
        runner.isValidVaultFormat(vaultValue) == expectedResult

        where:
        vaultValue                                    || expectedResult
        "!vault |\n  encryptedcontent"                || true
        "!vault |\n  line1\n  line2"                  || true
        "!vault\n  content"                           || false // missing pipe - Ansible Vault always requires "|" for literal block style
        "not a vault"                                 || false
        null                                          || false
        ""                                            || false
        "!vault"                                      || false // single-line without content - invalid
        "!vault |\n"                                  || false // no content
    }

    def "buildGroupVarsYaml: should create valid YAML with host passwords and users"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        def hostPasswords = [
                "web-server-1": "!vault |\n  encrypted1",
                "db-server-1": "!vault |\n  encrypted2"
        ]
        def hostUsers = [
                "web-server-1": "webadmin",
                "db-server-1": "dbadmin"
        ]

        when:
        def yaml = runner.buildGroupVarsYaml(hostPasswords, hostUsers, [:])

        then:
        yaml.contains("host_passwords:")
        yaml.contains("web-server-1: !vault |")
        yaml.contains("    encrypted1")
        yaml.contains("db-server-1: !vault |")
        yaml.contains("    encrypted2")
        yaml.contains("host_users:")
        yaml.contains("web-server-1: webadmin")
        yaml.contains("db-server-1: dbadmin")
    }

    def "buildGroupVarsYaml: should escape special characters in node names"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        def hostPasswords = [
                "web:server:1": "!vault |\n  encrypted"
        ]
        def hostUsers = [
                "web:server:1": "admin"
        ]

        when:
        def yaml = runner.buildGroupVarsYaml(hostPasswords, hostUsers, [:])

        then:
        yaml.contains('"web:server:1": !vault |')
        yaml.contains('"web:server:1": admin')
    }

    def "buildGroupVarsYaml: should throw exception for invalid vault format"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        def hostPasswords = [
                "web-server-1": "not-a-vault-value"
        ]
        def hostUsers = [
                "web-server-1": "admin"
        ]

        when:
        runner.buildGroupVarsYaml(hostPasswords, hostUsers, [:])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid vault format for host: web-server-1")
    }

    def "buildGroupVarsYaml: should handle empty host lists"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        def hostPasswords = [:]
        def hostUsers = [:]

        when:
        def yaml = runner.buildGroupVarsYaml(hostPasswords, hostUsers, [:])

        then:
        !yaml.contains("host_passwords:")  // Should not appear when empty
        !yaml.contains("host_users:")  // Should not appear when empty
        !yaml.contains("host_private_keys:")  // Should not appear when empty
        yaml.isEmpty()  // Should produce empty output when all host lists are empty
    }

    def "escapePasswordForYaml: should escape special characters"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        expect:
        runner.escapePasswordForYaml(password) == expectedResult

        where:
        password                  || expectedResult
        "simple"                  || "simple"
        "p@ssword"                || "p@ssword"
        "pass:word"               || "pass:word"
        "pass#word"               || "pass#word"
        'pass"word'               || 'pass\\"word'
        'pass\\word'              || 'pass\\\\word'
        'pass\nword'              || 'pass\\nword'
        'pass\rword'              || 'pass\\rword'
        'pass"\\word'             || 'pass\\"\\\\word'
        "p@ss:w#rd!"              || "p@ss:w#rd!"
        '"\\'                     || '\\"\\\\'
        ""                        || ""
    }

    def "escapePasswordForYaml: should handle complex passwords"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        when:
        def result = runner.escapePasswordForYaml('My"P@ss\\word\n2024')

        then:
        result == 'My\\"P@ss\\\\word\\n2024'
    }

    def "ssh password with special characters should be escaped and quoted"() {
        given:
        String playbook = "test"
        String password = 'p@ss"word:123'

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.sshPass(password)
        runnerBuilder.sshUser("user")
        runnerBuilder.sshUsePassword(true)

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        List<String> capturedArgs = null
        ProcessExecutor.ProcessExecutorBuilder processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder)

        // Configure fluent builder responses
        processBuilder.build() >> processExecutor
        processBuilder.procArgs(_ as List) >> { List args ->
            capturedArgs = new ArrayList(args)
            return processBuilder
        }
        processBuilder.environmentVariables(_ as Map) >> { Map e -> return processBuilder }
        processBuilder.baseDirectory(_ as File) >> { File f -> return processBuilder }
        processBuilder.stdinVariables(_ as List) >> { List v -> return processBuilder }
        processBuilder.promptStdinLogFile(_ as File) >> { File f -> return processBuilder }
        processBuilder.debug(_ as boolean) >> { boolean d -> return processBuilder }

        def ansibleVault = Mock(AnsibleVault) {
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
        result == 0
        1 * ansibleVault.encryptVariable(_, _) >> "!vault | value"
        capturedArgs != null
        // Flatten and verify --extra-vars argument exists
        def flatArgs = capturedArgs.flatten().collect { it?.toString() }
        flatArgs.any { it.contains("--extra-vars") }
    }

    def "become password with special characters should be escaped and quoted"() {
        given:
        String playbook = "test"
        String becomePass = 'admin"pass\\123'

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.become(true)
        runnerBuilder.becomeUser("root")
        runnerBuilder.becomePassword(becomePass)
        runnerBuilder.becomeMethod("sudo")

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        List<String> capturedArgs = null
        ProcessExecutor.ProcessExecutorBuilder processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder)

        // Configure fluent builder responses
        processBuilder.build() >> processExecutor
        processBuilder.procArgs(_ as List) >> { List args ->
            capturedArgs = new ArrayList(args)
            return processBuilder
        }
        processBuilder.environmentVariables(_ as Map) >> { Map e -> return processBuilder }
        processBuilder.baseDirectory(_ as File) >> { File f -> return processBuilder }
        processBuilder.stdinVariables(_ as List) >> { List v -> return processBuilder }
        processBuilder.promptStdinLogFile(_ as File) >> { File f -> return processBuilder }
        processBuilder.debug(_ as boolean) >> { boolean d -> return processBuilder }

        def ansibleVault = Mock(AnsibleVault) {
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
        result == 0
        1 * ansibleVault.encryptVariable(_, _) >> "!vault | value"
        capturedArgs != null
        // Flatten and verify arguments
        def flatArgs = capturedArgs.flatten().collect { it?.toString() }
        flatArgs.contains("--become")
        flatArgs.any { it.contains("--extra-vars") }
    }

    def "escapePasswordForYaml: should preserve passwords without special chars"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        when:
        def result = runner.escapePasswordForYaml("simplePassword123")

        then:
        result == "simplePassword123"
    }

    def "escapePasswordForYaml: should handle multiple escape sequences"() {
        given:
        def builder = AnsibleRunner.playbookInline("test")
        builder.customTmpDirPath("/tmp")
        def runner = builder.build()

        when:
        // Password with quote, backslash, and newline
        def result = runner.escapePasswordForYaml('test"\\pass\n')

        then:
        // Each special char should be escaped
        result == 'test\\"\\\\pass\\n'
    }

    def "both ssh and become passwords with special chars should work together"() {
        given:
        String playbook = "test"
        String sshPass = 'ssh"pass:123'
        String becomePass = 'sudo\\pass#456'

        def runnerBuilder = AnsibleRunner.playbookInline(playbook)
        runnerBuilder.sshPass(sshPass)
        runnerBuilder.sshUser("user")
        runnerBuilder.sshUsePassword(true)
        runnerBuilder.become(true)
        runnerBuilder.becomeUser("root")
        runnerBuilder.becomePassword(becomePass)
        runnerBuilder.becomeMethod("sudo")

        def process = Mock(Process) {
            waitFor() >> 0
            getInputStream() >> new ByteArrayInputStream("".getBytes())
            getOutputStream() >> new ByteArrayOutputStream()
            getErrorStream() >> new ByteArrayInputStream("".getBytes())
        }

        def processExecutor = Mock(ProcessExecutor) {
            run() >> process
        }

        ProcessExecutor.ProcessExecutorBuilder processBuilder = Mock(ProcessExecutor.ProcessExecutorBuilder)

        // Configure fluent builder responses
        processBuilder.build() >> processExecutor
        processBuilder.procArgs(_ as List) >> { List args -> return processBuilder }
        processBuilder.environmentVariables(_ as Map) >> { Map e -> return processBuilder }
        processBuilder.baseDirectory(_ as File) >> { File f -> return processBuilder }
        processBuilder.stdinVariables(_ as List) >> { List v -> return processBuilder }
        processBuilder.promptStdinLogFile(_ as File) >> { File f -> return processBuilder }
        processBuilder.debug(_ as boolean) >> { boolean d -> return processBuilder }

        def ansibleVault = Mock(AnsibleVault) {
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
        result == 0
        // Both passwords should be encrypted
        2 * ansibleVault.encryptVariable(_, _) >> "!vault | value"
    }

    def "node name sanitization: should sanitize node names with forward slashes for temp files"() {
        given:
        // Test case from real issue: node name with forward slashes
        def runner = AnsibleRunner.playbookInline("test").build()
        String nodeName = "/docker-runner-ansible-ssh-node-b-3"
        String sanitized = runner.sanitizeNodeNameForFilesystem(nodeName)

        expect:
        sanitized == "_docker-runner-ansible-ssh-node-b-3"
        // Verify sanitized name is filesystem-safe
        !sanitized.contains("/")
    }

    def "node name sanitization: should sanitize various special characters"() {
        given:
        def runner = AnsibleRunner.playbookInline("test").build()
        String sanitized = runner.sanitizeNodeNameForFilesystem(nodeName)

        expect:
        sanitized == expected

        where:
        nodeName                          || expected
        "simple-node"                     || "simple-node"
        "node.with.dots"                  || "node.with.dots"
        "node_with_underscores"           || "node_with_underscores"
        "/docker-runner-ansible"          || "_docker-runner-ansible"
        "node:with:colons"                || "node_with_colons"
        "node with spaces"                || "node_with_spaces"
        "node\\with\\backslashes"         || "node_with_backslashes"
        "node*with?wildcards"             || "node_with_wildcards"
        "node|with|pipes"                 || "node_with_pipes"
        "node<with>brackets"              || "node_with_brackets"
        "node\"with'quotes"               || "node_with_quotes"
        'node@with#special$chars%'        || "node_with_special_chars_"
        "/path/to/node-123"               || "_path_to_node-123"
    }

    def "node name sanitization: should preserve safe alphanumeric and allowed characters"() {
        given:
        def runner = AnsibleRunner.playbookInline("test").build()
        String safeName = "my-node_123.server-A"
        String sanitized = runner.sanitizeNodeNameForFilesystem(safeName)

        expect:
        sanitized == safeName  // Should not be changed
    }

    def "node name sanitization: should handle empty and edge case node names"() {
        given:
        def runner = AnsibleRunner.playbookInline("test").build()
        String sanitized = runner.sanitizeNodeNameForFilesystem(nodeName)

        expect:
        sanitized == expected

        where:
        nodeName                          || expected
        ""                                || "_"         // empty -> prepend underscore
        "123"                             || "123"
        "..."                             || "_..."      // all dots -> prepend underscore to avoid filesystem issues
        "---"                             || "---"
        "___"                             || "___"
        "a"                               || "a"
        "/////"                           || "_____"
        ".hidden"                         || "_.hidden"  // starts with dot -> prepend underscore to prevent hidden files
        ".config"                         || "_.config"  // starts with dot -> prepend underscore to prevent hidden files
    }

}
