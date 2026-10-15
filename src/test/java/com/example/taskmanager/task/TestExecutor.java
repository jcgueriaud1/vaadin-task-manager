package com.example.taskmanager.task;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * An {@link Executor} that the test drives. Submitted work waits in a queue
 * until the test calls {@link #runNext()}. The work then runs on its own thread
 * until it calls {@link #pause()} or ends, and only then does control return to
 * the test. {@link #resume()} continues the paused work the same way.
 * <p>
 * Test and worker never run at the same time, so tests are deterministic
 * without sleeps or polling.
 */
public class TestExecutor implements Executor {

    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final Semaphore toWorker = new Semaphore(0);
    private final Semaphore toTest = new Semaphore(0);
    private volatile Thread worker;

    @Override
    public synchronized void execute(Runnable command) {
        queue.add(command);
    }

    public synchronized int queued() {
        return queue.size();
    }

    /** Starts the oldest queued work and waits until it pauses or ends. */
    public void runNext() {
        Runnable command;
        synchronized (this) {
            if (worker != null) {
                throw new IllegalStateException("Work is already paused; resume it first");
            }
            command = queue.poll();
        }
        if (command == null) {
            throw new IllegalStateException("No queued work");
        }
        // Assign before starting: fast work may end before start() returns
        worker = Thread.ofVirtual().unstarted(() -> {
            try {
                command.run();
            } finally {
                worker = null;
                toTest.release();
            }
        });
        worker.start();
        toTest.acquireUninterruptibly();
    }

    /** Continues the paused work and waits until it pauses again or ends. */
    public void resume() {
        if (worker == null) {
            throw new IllegalStateException("No paused work");
        }
        toWorker.release();
        toTest.acquireUninterruptibly();
    }

    /** Runs the paused work, then all queued work, to the end. */
    public void runAll() {
        while (worker != null) {
            resume();
        }
        while (queued() > 0) {
            runNext();
            while (worker != null) {
                resume();
            }
        }
    }

    /** Called from the work: hands control back to the test. */
    public void pause() {
        if (Thread.currentThread() != worker) {
            throw new IllegalStateException("pause() must be called from running work");
        }
        toTest.release();
        toWorker.acquireUninterruptibly();
    }
}
