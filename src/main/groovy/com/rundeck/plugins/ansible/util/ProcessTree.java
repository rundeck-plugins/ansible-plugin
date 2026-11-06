package com.rundeck.plugins.ansible.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Stops a process and every process it started.
 *
 * Process.destroy() signals only the top process. When an execution is aborted or times out,
 * the processes that ansible-playbook forked (workers, mitogen multiplexers, ssh) keep running
 * under PID 1. This class sends SIGTERM to the whole tree, then SIGKILL to what is left after a grace period.
 */
public final class ProcessTree {

    public static final long DEFAULT_GRACE_MILLIS = 10_000;

    private static final long POLL_MILLIS = 100;

    private ProcessTree() {
    }

    public static void destroy(Process proc) {
        destroy(proc, DEFAULT_GRACE_MILLIS);
    }

    public static void destroy(Process proc, long graceMillis) {
        if (proc == null || !proc.isAlive()) {
            return;
        }
        // List the tree before any signal: a child whose parent exits moves to PID 1 and leaves descendants().
        List<ProcessHandle> tree = new ArrayList<>();
        tree.add(proc.toHandle());
        tree.addAll(proc.descendants().collect(Collectors.toList()));
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
