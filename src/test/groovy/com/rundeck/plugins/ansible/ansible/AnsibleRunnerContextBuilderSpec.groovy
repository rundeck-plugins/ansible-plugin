package com.rundeck.plugins.ansible.ansible

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeSet
import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.ExecutionLogger
import com.dtolabs.rundeck.core.utils.PropertyLookup
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.rundeck.plugins.ansible.plugin.AnsiblePluginGroup
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AnsibleRunnerContextBuilderSpec extends Specification {

    @TempDir
    File baseTmpDir

    def "test plugin group"(){
        given:

        PluginStepContext context = Mock(PluginStepContext){
            getDataContext() >> ['job': ['loglevel':'INFO']]
            getExecutionContext() >> Mock(ExecutionContext){
                getDataContext() >> [:]
                getExecutionLogger() >> Mock(ExecutionLogger)
            }
            getFramework() >> Mock(Framework)
            getNodes() >> Mock(INodeSet){
                getNodes() >> []
            }
        }

        Map<String, Object> configuration = [
                'ansible-playbook' : 'path/to/playbook'
        ]

        AnsiblePluginGroup pluginGroup = new AnsiblePluginGroup()
        pluginGroup.setAnsibleConfigFilePath("/etc/ansible/ansible.cfg")
        pluginGroup.setEncryptExtraVars(true)
        pluginGroup.setAnsibleBinariesDirPath("/usr/local/lib")

        when:
        AnsibleRunnerContextBuilder contextBuilder = new AnsibleRunnerContextBuilder(context.getExecutionContext(),
                context.getFramework(),
                context.getNodes(),
                configuration,
                pluginGroup)

        then:
        contextBuilder.getConfigFile() == "/etc/ansible/ansible.cfg"
        contextBuilder.getBinariesFilePath() == "/usr/local/lib"
        contextBuilder.encryptExtraVars()
        contextBuilder.getPlaybookPath() == "path/to/playbook"
    }

    def "test plugin group not set"(){
        given:

        PluginStepContext context = Mock(PluginStepContext){
            getDataContext() >> ['job': ['loglevel':'INFO']]
            getExecutionContext() >> Mock(ExecutionContext){
                getDataContext() >> [:]
            }
            getFramework() >> Mock(Framework)
            getNodes() >> Mock(INodeSet){
                getNodes() >> []
            }
        }

        Map<String, Object> configuration = [
                'ansible-playbook' : 'path/to/playbook',
                'ansible-config-file-path': '/etc/ansible/ansible.cfg'
        ]

        when:
        AnsibleRunnerContextBuilder contextBuilder = new AnsibleRunnerContextBuilder(context.getExecutionContext(),
                context.getFramework(),
                context.getNodes(),
                configuration,
                null)

        then:
        contextBuilder.getConfigFile() == "/etc/ansible/ansible.cfg"
        contextBuilder.getBinariesFilePath() == null
        !contextBuilder.encryptExtraVars()
        contextBuilder.getPlaybookPath() == "path/to/playbook"
    }

    // ----------------------------------------------------------------------------
    // Race condition fix: getExecutionSpecificTmpDir() / cleanupTempFiles()
    // See ansible-plugin-race-condition-context.md
    // ----------------------------------------------------------------------------

    /**
     * Builds a fresh AnsibleRunnerContextBuilder wired so that
     * AnsibleUtil.getCustomTmpPathDir(framework) resolves to {@code baseTmpDir} and
     * the data context exposes the given {@code execId}.
     */
    private AnsibleRunnerContextBuilder newBuilder(String execId) {
        def propertyLookup = Mock(PropertyLookup)
        propertyLookup.getProperty("framework.tmp.dir") >> baseTmpDir.absolutePath

        def framework = Mock(Framework)
        framework.getPropertyLookup() >> propertyLookup
        framework.hasProjectProperty(_, _) >> false
        framework.hasProperty(_) >> false

        def execContext = Mock(ExecutionContext)
        execContext.getDataContext() >> ['job': ['execid': execId]]
        execContext.getFrameworkProject() >> 'test-project'

        def nodes = Mock(INodeSet)
        nodes.getNodes() >> []

        return new AnsibleRunnerContextBuilder(execContext, framework, nodes, [:])
    }

    /**
     * Drops a fake "inventory" file inside the builder's working directory, mirroring
     * what AnsibleInventoryBuilder.buildInventory() does in production.
     */
    private File seedInventoryFile(AnsibleRunnerContextBuilder builder) {
        File dir = new File(builder.getExecutionSpecificTmpDir())
        File inventory = File.createTempFile("ansible-inventory", ".json", dir)
        inventory.text = '{"all":{"hosts":{}}}'
        return inventory
    }

    def "two builders with the same executionId get distinct directories with the executionId prefix"() {
        given:
        def builderA = newBuilder("4242")
        def builderB = newBuilder("4242")

        when:
        File dirA = new File(builderA.getExecutionSpecificTmpDir())
        File dirB = new File(builderB.getExecutionSpecificTmpDir())

        then: "both directories exist"
        dirA.exists() && dirA.isDirectory()
        dirB.exists() && dirB.isDirectory()

        and: "they are different directories"
        dirA.absolutePath != dirB.absolutePath

        and: "they sit directly under the configured base tmp dir (no shared parent)"
        dirA.parentFile.absolutePath == baseTmpDir.absolutePath
        dirB.parentFile.absolutePath == baseTmpDir.absolutePath

        and: "their names start with the ansible-exec-<execId>-builder- prefix for filtering"
        dirA.name.startsWith("ansible-exec-4242-builder-")
        dirB.name.startsWith("ansible-exec-4242-builder-")
    }

    def "directories of distinct executions can coexist under the same base tmp dir"() {
        given:
        def builderX = newBuilder("4242")
        def builderY = newBuilder("9999")

        when:
        File dirX = new File(builderX.getExecutionSpecificTmpDir())
        File dirY = new File(builderY.getExecutionSpecificTmpDir())

        then:
        dirX.parentFile.absolutePath == baseTmpDir.absolutePath
        dirY.parentFile.absolutePath == baseTmpDir.absolutePath
        dirX.name.startsWith("ansible-exec-4242-builder-")
        dirY.name.startsWith("ansible-exec-9999-builder-")
    }

    def "cleanupTempFiles on one builder must not delete a sibling's inventory"() {
        given:
        def builderA = newBuilder("9001")
        def builderB = newBuilder("9001")

        File invA = seedInventoryFile(builderA)
        File invB = seedInventoryFile(builderB)
        File dirA = new File(builderA.getExecutionSpecificTmpDir())
        File dirB = new File(builderB.getExecutionSpecificTmpDir())

        expect:
        invA.exists()
        invB.exists()

        when: "builder A finishes and cleans up"
        builderA.cleanupTempFiles()

        then: "A's directory and inventory are gone, but B's is intact"
        !dirA.exists()
        !invA.exists()
        dirB.exists()
        invB.exists()

        when: "builder B finishes and cleans up"
        builderB.cleanupTempFiles()

        then: "B's directory is also gone"
        !dirB.exists()
        !invB.exists()
    }

    def "parallel builders never lose their inventory files before their own cleanup"() {
        given:
        int parallelism = 16
        ExecutorService pool = Executors.newFixedThreadPool(parallelism)
        CyclicBarrier barrier = new CyclicBarrier(parallelism)
        CountDownLatch done = new CountDownLatch(parallelism)
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>()

        when:
        parallelism.times {
            pool.submit({
                AnsibleRunnerContextBuilder builder = null
                try {
                    builder = newBuilder("55555")
                    File workDir = new File(builder.getExecutionSpecificTmpDir())
                    File inventory = File.createTempFile("ansible-inventory", ".json", workDir)
                    inventory.text = '{"all":{"hosts":{}}}'

                    // All threads have created their own dir+inventory; now hold them open
                    // simultaneously to maximise the chance of a sibling's cleanup running while
                    // ours is still alive.
                    barrier.await(10, TimeUnit.SECONDS)

                    if (!inventory.exists()) {
                        failures.add("inventory disappeared while builder was alive: ${inventory.absolutePath}")
                    }
                    if (!workDir.exists()) {
                        failures.add("work dir disappeared while builder was alive: ${workDir.absolutePath}")
                    }
                } catch (Throwable t) {
                    failures.add("worker threw: ${t.message}")
                } finally {
                    builder?.cleanupTempFiles()
                    done.countDown()
                }
            } as Runnable)
        }

        boolean finished = done.await(30, TimeUnit.SECONDS)
        pool.shutdownNow()

        then:
        finished
        failures.isEmpty()

        and: "no leftover ansible-exec-55555-builder-* directories remain after cleanup"
        baseTmpDir.listFiles({ f -> f.name.startsWith("ansible-exec-55555-builder-") } as FileFilter).length == 0
    }

    def "debug mode preserves the per-builder directory"() {
        given:
        def builder = newBuilder("debug-123")
        // ansible-debug=true must short-circuit cleanup paths.
        Map<String, Object> jobConf = (Map<String, Object>) builder.getJobConf()
        jobConf.put("ansible-debug", "true")

        File workDir = new File(builder.getExecutionSpecificTmpDir())
        File inventory = seedInventoryFile(builder)

        when:
        builder.cleanupTempFiles()

        then: "nothing is deleted in debug mode, mirroring legacy behaviour"
        inventory.exists()
        workDir.exists()
        workDir.name.startsWith("ansible-exec-debug-123-builder-")
    }
}
