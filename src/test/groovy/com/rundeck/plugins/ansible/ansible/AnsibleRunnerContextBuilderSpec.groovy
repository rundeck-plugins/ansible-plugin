package com.rundeck.plugins.ansible.ansible

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.common.INodeEntry
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

    // -------------------------------------------------------------------------
    // getBaseDir() — property resolution hierarchy (RUN-4228)
    // Priority: jobConf > node attribute > project property > framework property
    // -------------------------------------------------------------------------

    private AnsibleRunnerContextBuilder baseDirBuilder(Map params = [:]) {
        def jobConf = (params.jobConf ?: [:]) as Map<String, Object>
        INodeEntry node = params.node as INodeEntry
        String projectValue = params.projectValue
        String frameworkValue = params.frameworkValue
        Map dataContext = params.dataContext ?: [:]

        def propertyLookup = Mock(PropertyLookup)
        propertyLookup.getProperty("framework.tmp.dir") >> baseTmpDir.absolutePath

        def framework = Mock(Framework)
        framework.getPropertyLookup() >> propertyLookup
        // Mock returns false/null by default for unmatched calls — only stub what each test needs

        if (projectValue != null) {
            framework.hasProjectProperty(
                AnsibleDescribable.PROJ_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BASE_DIR_PATH,
                'test-project'
            ) >> true
            framework.getProjectProperty(
                'test-project',
                AnsibleDescribable.PROJ_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BASE_DIR_PATH
            ) >> projectValue
        }

        if (frameworkValue != null) {
            framework.hasProperty(
                AnsibleDescribable.FWK_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BASE_DIR_PATH
            ) >> true
            framework.getProperty(
                AnsibleDescribable.FWK_PROP_PREFIX + AnsibleDescribable.ANSIBLE_BASE_DIR_PATH
            ) >> frameworkValue
        }

        def execContext = Mock(ExecutionContext)
        execContext.getDataContext() >> dataContext
        execContext.getFrameworkProject() >> 'test-project'

        if (node != null) {
            return new AnsibleRunnerContextBuilder(node, execContext, framework, jobConf)
        }

        def nodeSet = Mock(INodeSet)
        nodeSet.getNodes() >> []
        return new AnsibleRunnerContextBuilder(execContext, framework, nodeSet, jobConf)
    }

    def "getBaseDir returns null when not configured at any level"() {
        expect:
        baseDirBuilder().getBaseDir() == null
    }

    def "getBaseDir returns value from job configuration"() {
        given:
        def builder = baseDirBuilder(jobConf: [(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH): '/job/playbooks'])

        expect:
        builder.getBaseDir() == '/job/playbooks'
    }

    def "getBaseDir returns project-level property when not set in job config"() {
        given:
        def builder = baseDirBuilder(projectValue: '/project/playbooks')

        expect:
        builder.getBaseDir() == '/project/playbooks'
    }

    def "getBaseDir returns framework-level property when neither job config nor project is set"() {
        given:
        def builder = baseDirBuilder(frameworkValue: '/framework/playbooks')

        expect:
        builder.getBaseDir() == '/framework/playbooks'
    }

    def "getBaseDir returns node attribute when set and job config is empty"() {
        given:
        def node = Mock(INodeEntry) {
            getAttributes() >> [(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH): '/node/playbooks']
        }
        def builder = baseDirBuilder(node: node)

        expect:
        builder.getBaseDir() == '/node/playbooks'
    }

    def "getBaseDir job config takes precedence over project property"() {
        given:
        def builder = baseDirBuilder(
            jobConf: [(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH): '/job/playbooks'],
            projectValue: '/project/playbooks'
        )

        expect:
        builder.getBaseDir() == '/job/playbooks'
    }

    def "getBaseDir project property takes precedence over framework property"() {
        given:
        def builder = baseDirBuilder(
            projectValue: '/project/playbooks',
            frameworkValue: '/framework/playbooks'
        )

        expect:
        builder.getBaseDir() == '/project/playbooks'
    }

    def "getBaseDir node attribute takes precedence over project property"() {
        given:
        def node = Mock(INodeEntry) {
            getAttributes() >> [(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH): '/node/playbooks']
        }
        def builder = baseDirBuilder(node: node, projectValue: '/project/playbooks')

        expect:
        builder.getBaseDir() == '/node/playbooks'
    }

    def "getBaseDir falls back to PluginGroup when no property is set at any other level"() {
        given:
        def pluginGroup = new AnsiblePluginGroup()
        pluginGroup.setAnsibleBaseDirPath('/plugingroup/playbooks')

        def propertyLookup = Mock(PropertyLookup)
        propertyLookup.getProperty("framework.tmp.dir") >> baseTmpDir.absolutePath
        def framework = Mock(Framework) { getPropertyLookup() >> propertyLookup }
        def execContext = Mock(ExecutionContext) {
            getDataContext() >> [:]
            getFrameworkProject() >> 'test-project'
            getExecutionLogger() >> Mock(ExecutionLogger)
        }
        def nodeSet = Mock(INodeSet) { getNodes() >> [] }
        def builder = new AnsibleRunnerContextBuilder(execContext, framework, nodeSet, [:], pluginGroup)

        expect:
        builder.getBaseDir() == '/plugingroup/playbooks'
    }

    def "getBaseDir job config takes precedence over PluginGroup"() {
        given:
        def pluginGroup = new AnsiblePluginGroup()
        pluginGroup.setAnsibleBaseDirPath('/plugingroup/playbooks')

        def propertyLookup = Mock(PropertyLookup)
        propertyLookup.getProperty("framework.tmp.dir") >> baseTmpDir.absolutePath
        def framework = Mock(Framework) { getPropertyLookup() >> propertyLookup }
        def execContext = Mock(ExecutionContext) {
            getDataContext() >> [:]
            getFrameworkProject() >> 'test-project'
        }
        def nodeSet = Mock(INodeSet) { getNodes() >> [] }
        def builder = new AnsibleRunnerContextBuilder(
            execContext, framework, nodeSet,
            [(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH): '/job/playbooks'],
            pluginGroup
        )

        expect:
        builder.getBaseDir() == '/job/playbooks'
    }

    def "getBaseDir interpolates data context variables in the path"() {
        given:
        def builder = baseDirBuilder(
            jobConf: [(AnsibleDescribable.ANSIBLE_BASE_DIR_PATH): '/opt/ansible/${job.project}/playbooks'],
            dataContext: ['job': ['project': 'my-project']]
        )

        expect:
        builder.getBaseDir() == '/opt/ansible/my-project/playbooks'
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
