package com.rundeck.plugins.ansible.util;

import java.util.ArrayList;
import java.util.HashSet;
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
 * children that are still alive too, and everything they started since.
 *
 * Recording works on snapshots: a process that starts and loses every recorded ancestor between
 * two snapshots is not seen. Java cannot put ansible-playbook in its own process group, which
 * would close that gap, so the interval is kept short and each snapshot also follows the
 * recorded processes, not only the top one.
 */
public final class ProcessTree {

    public static final long DEFAULT_GRACE_MILLIS = 10_000;

    public static final long DEFAULT_RECORD_MILLIS = 1_000;

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
        // Follow the recorded processes too: a worker whose parent exited no longer shows up
        // under proc, but the processes it starts still belong to this execution. proc goes
        // first, so only the recorded processes outside its subtree are expanded again.
        ProcessHandle top = proc.toHandle();
        Set<ProcessHandle> tree = new LinkedHashSet<>();
        tree.add(top);
        tree.addAll(recorded);
        expand(tree);
        tree.remove(top);
        recorded.addAll(tree);
    }

    /**
     * Adds the current descendants of every live process in tree, and returns the ones that were
     * not in it yet. Each subtree is listed once: a process already found under another one is
     * not expanded again.
     */
    private static Set<ProcessHandle> expand(Set<ProcessHandle> tree) {
        Set<ProcessHandle> covered = new HashSet<>();
        Set<ProcessHandle> found = new LinkedHashSet<>();
        for (ProcessHandle handle : new ArrayList<>(tree)) {
            if (covered.contains(handle) || !handle.isAlive()) {
                continue;
            }
            handle.descendants().forEach(child -> {
                covered.add(child);
                if (tree.add(child)) {
                    found.add(child);
                }
            });
        }
        return found;
    }

    /**
     * Stops the process, its descendants, the recorded descendants that are still alive, and the
     * current descendants of those.
     */
    public void destroy() {
        destroy(DEFAULT_GRACE_MILLIS);
    }

    public void destroy(long graceMillis) {
        // List the tree before any signal: a child whose parent exits moves to PID 1 and leaves descendants().
        Set<ProcessHandle> tree = new LinkedHashSet<>();
        if (proc.isAlive()) {
            tree.add(proc.toHandle());
        }
        recorded.stream().filter(ProcessHandle::isAlive).forEach(tree::add);
        expand(tree);
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

        // A process that survives SIGTERM can still fork. Look for new children while waiting and
        // once more before SIGKILL, so they get the same signals.
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(graceMillis);
        try {
            while (System.nanoTime() < deadline && tree.stream().anyMatch(ProcessHandle::isAlive)) {
                Thread.sleep(POLL_MILLIS);
                expand(tree).forEach(ProcessHandle::destroy);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        expand(tree);
        tree.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }
}
