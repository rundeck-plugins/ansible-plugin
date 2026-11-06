package com.rundeck.plugins.ansible.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Stops a process and every process it started.
 *
 * Process.destroy() signals only the top process. When an execution is aborted or times out,
 * the processes that ansible-playbook forked (workers, mitogen multiplexers, ssh) keep running
 * under PID 1. This class sends SIGTERM to the whole tree, then SIGKILL to what is left after a grace period.
 *
 * {@link #waitFor()} also records the descendants while the process runs. A child that outlives
 * its parent moves to PID 1 and leaves descendants(), so {@link #destroy()} stops the recorded
 * children that are still alive too.
 */
public final class ProcessTree {

    public static final long DEFAULT_GRACE_MILLIS = 10_000;

    public static final long DEFAULT_RECORD_MILLIS = 5_000;

    private static final long POLL_MILLIS = 100;

    private final Process proc;

    private final long recordMillis;

    private final Set<ProcessHandle> recorded = ConcurrentHashMap.newKeySet();

    public ProcessTree(Process proc) {
        this(proc, DEFAULT_RECORD_MILLIS);
    }

    public ProcessTree(Process proc, long recordMillis) {
        this.proc = proc;
        this.recordMillis = recordMillis;
    }

    /**
     * Waits for the process to exit, like {@link Process#waitFor()}, and records its descendants
     * at each interval.
     */
    public int waitFor() throws InterruptedException {
        while (proc.isAlive()) {
            record();
            if (proc.waitFor(recordMillis, TimeUnit.MILLISECONDS)) {
                break;
            }
        }
        return proc.waitFor();
    }

    private void record() {
        recorded.removeIf(handle -> !handle.isAlive());
        proc.descendants().forEach(recorded::add);
    }

    /** Stops the process, its descendants, and the recorded descendants that are still alive. */
    public void destroy() {
        destroy(DEFAULT_GRACE_MILLIS);
    }

    public void destroy(long graceMillis) {
        // List the tree before any signal: a child whose parent exits moves to PID 1 and leaves descendants().
        Set<ProcessHandle> tree = new LinkedHashSet<>();
        if (proc.isAlive()) {
            tree.add(proc.toHandle());
            proc.descendants().forEach(tree::add);
        }
        recorded.stream().filter(ProcessHandle::isAlive).forEach(tree::add);
        destroyAll(tree, graceMillis);
    }

    /** Stops a process and its current descendants. Does nothing for a null or finished process. */
    public static void destroy(Process proc) {
        destroy(proc, DEFAULT_GRACE_MILLIS);
    }

    public static void destroy(Process proc, long graceMillis) {
        if (proc == null || !proc.isAlive()) {
            return;
        }
        new ProcessTree(proc).destroy(graceMillis);
    }

    private static void destroyAll(Set<ProcessHandle> tree, long graceMillis) {
        if (tree.isEmpty()) {
            return;
        }
        tree.forEach(ProcessHandle::destroy);

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(graceMillis);
        try {
            while (System.nanoTime() < deadline && tree.stream().anyMatch(ProcessHandle::isAlive)) {
                Thread.sleep(POLL_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        tree.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }
}
