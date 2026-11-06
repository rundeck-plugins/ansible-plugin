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
        String text
        try {
            text = new String(Files.readAllBytes(Paths.get("/proc/${handle.pid()}/stat")))
        } catch (IOException ignored) {
            // The process exited, possibly between two checks.
            return false
        }
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

    def "waitFor returns the exit code"() {
        given:
        def proc = new ProcessBuilder("bash", "-c", "sleep 0.3; exit 3").start()

        expect:
        new ProcessTree(proc, 100).waitFor() == 3
    }

    def "destroy stops recorded children after the parent has exited"() {
        given: 'a parent that exits while its two children keep running'
        def proc = startTree('sleep 300 & sleep 300 & sleep 1', 2)
        def children = proc.descendants().findAll { it.info().commandLine().orElse('').contains('300') }
        def tree = new ProcessTree(proc, 100)

        when: 'the parent exits, and the static destroy no longer sees the children'
        tree.waitFor()
        ProcessTree.destroy(proc, 500)

        then:
        children.size() == 2
        children.every { running(it) }

        when:
        tree.destroy(2000)

        then:
        waitUntil(2000) { children.every { !running(it) } }
    }

    def "destroy does nothing for a finished or missing process"() {
        given:
        def proc = new ProcessBuilder("true").start()
        proc.waitFor()

        when:
        ProcessTree.destroy(proc)
        ProcessTree.destroy(null)
        new ProcessTree(proc).destroy()

        then:
        noExceptionThrown()
    }
}
