package com.rundeck.plugins.ansible.util

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

@Requires({ os.linux })
class ProcessTreeSpec extends Specification {

    List<ProcessHandle> started = []

    def cleanup() {
        started.findAll { it.isAlive() }.each { it.destroyForcibly() }
    }

    // A zombie counts as stopped: it holds no memory and only waits for its new parent to reap it.
    static boolean running(ProcessHandle handle) {
        def stat = Paths.get("/proc/${handle.pid()}/stat")
        if (!Files.exists(stat)) {
            return false
        }
        def text = new String(Files.readAllBytes(stat))
        def state = text.substring(text.lastIndexOf(')') + 2, text.lastIndexOf(')') + 3)
        return state != 'Z' && state != 'X'
    }

    static boolean waitUntil(long millis, Closure<Boolean> condition) {
        def deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            sleep(50)
        }
        return condition()
    }

    Process startTree(String script, int children) {
        def proc = new ProcessBuilder("bash", "-c", script).start()
        started << proc.toHandle()
        assert waitUntil(5000) { proc.descendants().count() >= children }
        started.addAll(proc.descendants().collect())
        return proc
    }

    def "Process.destroy alone leaves the children running"() {
        given:
        def proc = startTree('sleep 300 & sleep 300 & wait', 2)
        def children = proc.descendants().collect()

        when:
        proc.destroy()
        proc.waitFor()

        then:
        children.size() == 2
        children.every { running(it) }
    }

    def "destroy stops the process and all of its children"() {
        given:
        def proc = startTree('sleep 300 & sleep 300 & wait', 2)
        def children = proc.descendants().collect()

        when:
        ProcessTree.destroy(proc, 5000)

        then:
        children.size() == 2
        waitUntil(2000) { !running(proc.toHandle()) && children.every { !running(it) } }
    }

    def "destroy kills children that ignore SIGTERM after the grace period"() {
        given:
        def proc = startTree('trap "" TERM; sleep 300 & sleep 300 & wait', 2)
        def children = proc.descendants().collect()
        def start = System.currentTimeMillis()

        when:
        ProcessTree.destroy(proc, 500)

        then:
        System.currentTimeMillis() - start >= 500
        waitUntil(2000) { !running(proc.toHandle()) && children.every { !running(it) } }
    }

    def "destroy does nothing for a finished or missing process"() {
        given:
        def proc = new ProcessBuilder("true").start()
        proc.waitFor()

        when:
        ProcessTree.destroy(proc)
        ProcessTree.destroy(null)

        then:
        noExceptionThrown()
    }
}
