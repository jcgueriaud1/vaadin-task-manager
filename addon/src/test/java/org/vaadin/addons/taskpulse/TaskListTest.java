package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;
import org.vaadin.addons.taskpulse.PulseAction.Retry;

class TaskListTest {

    private final TestExecutor executor = new TestExecutor();
    private final PulseTaskList taskList = new PulseTaskList();
    private final PulseAction newsletter = new PulseAction("Send newsletter", BlockingMode.NON_BLOCKING);
    private final PulseAction report = new PulseAction("Generate report", BlockingMode.NON_BLOCKING);
    private final PulseAction sync = new PulseAction("Sync with server", BlockingMode.NON_BLOCKING);
    private final PulseAction export = new PulseAction("Export data", BlockingMode.NON_BLOCKING);

    @Test
    void dismissedTaskLeavesTheTrayButStaysInTheTaskList() {
        PulseTask sent = taskList.start(newsletter, executor, ctx -> {});
        PulseTask generated = taskList.start(report, executor, ctx -> {});
        executor.runAll();

        sent.dismiss();

        assertEquals(List.of(generated), taskList.trayTasks().peek());
        assertEquals(List.of(sent, generated), taskList.tasks().peek().stream().toList());
    }

    @Test
    void aRunningTaskCannotBeDismissed() {
        PulseTask running = taskList.start(newsletter, executor, ctx -> {});

        assertThrows(IllegalStateException.class, () -> running.dismiss());
        assertEquals(List.of(running), taskList.trayTasks().peek());
    }

    @Test
    void runningCountFollowsTasksStartingAndFinishing() {
        assertEquals(0, taskList.runningCount().peek());

        taskList.start(newsletter, executor, ctx -> {});
        taskList.start(report, executor, ctx -> {});
        assertEquals(2, taskList.runningCount().peek());

        executor.runNext();
        assertEquals(1, taskList.runningCount().peek());

        executor.runAll();
        assertEquals(0, taskList.runningCount().peek());
    }

    @Test
    void retryStartsANewTaskWithTheSameWorkOnTheSameExecutorAndLeavesTheOldOneAsItWas() {
        List<String> runs = new ArrayList<>();
        PulseTask failed = taskList.start(sync, executor, ctx -> {
            runs.add("sync");
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        assertTrue(failed.retryable().peek());

        PulseTask returned = failed.retry();

        assertEquals(2, taskList.tasks().peek().size());
        PulseTask retried = task(1);
        assertSame(retried, returned);
        assertEquals(sync, retried.action());
        assertEquals(PulseTask.State.RUNNING, retried.state().peek());
        assertEquals(1, executor.queued(), "the retry waits on the same executor");
        executor.runAll();
        assertEquals(List.of("sync", "sync"), runs);
        assertEquals(PulseTask.State.FAILED, retried.state().peek());
        assertEquals(PulseTask.State.FAILED, failed.state().peek());
        assertEquals(Optional.of(new Outcome.Failed("Server unreachable")), failed.outcome().peek());
    }

    @Test
    void aTaskOfANeverRetriedActionCannotBeRetried() {
        PulseAction charge = new PulseAction("Charge card", BlockingMode.NON_BLOCKING, Retry.NEVER);
        PulseTask failed = taskList.start(charge, executor, ctx -> {
            throw new IllegalStateException("Card declined");
        });
        executor.runAll();

        assertFalse(failed.retryable().peek());
        assertThrows(IllegalStateException.class, () -> failed.retry());
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void retryIsRefusedForASucceededTask() {
        PulseTask succeeded = taskList.start(newsletter, Runnable::run, ctx -> {});

        assertThrows(IllegalStateException.class, () -> succeeded.retry());
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void retryIsRefusedWhileTheActionAlreadyHasARunningTask() {
        PulseTask failed = taskList.start(sync, executor, ctx -> {
            executor.pause();
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        failed.retry();

        assertThrows(IllegalStateException.class, () -> failed.retry());
        assertEquals(2, taskList.tasks().peek().size());
    }

    @Test
    void clearFinishedRemovesEveryFinishedTaskAndKeepsTheRunningOnes() {
        taskList.start(newsletter, executor, ctx -> {});
        taskList.start(sync, executor, ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        PulseTask running = taskList.start(export, executor, ctx -> executor.pause());
        executor.runNext();

        taskList.clearFinished();

        assertEquals(List.of(running), taskList.tasks().peek().stream().toList());
    }

    @Test
    void theTaskListKeepsAtMostFiftyTasksEvictingTheOldestFinishedFirst() {
        PulseTask running = taskList.start(report, executor, ctx -> {});
        for (int i = 0; i < 49; i++) {
            // Run on the test's thread: any Executor will do
            taskList.start(newsletter, Runnable::run, ctx -> {});
        }
        List<PulseTask> before = taskList.tasks().peek().stream().toList();
        PulseTask oldestFinished = before.get(1);
        PulseTask secondOldestFinished = before.get(2);
        assertEquals(50, before.size());

        PulseTask newest = taskList.start(sync, executor, ctx -> {});

        List<PulseTask> tasks = taskList.tasks().peek().stream().toList();
        assertEquals(50, tasks.size());
        assertSame(running, tasks.get(0));
        assertSame(secondOldestFinished, tasks.get(1));
        assertFalse(tasks.contains(oldestFinished));
        assertSame(newest, tasks.get(49));
    }

    @Test
    void overallProgressAveragesRunningTasksThatReportProgress() {
        assertEquals(Optional.empty(), taskList.overallProgress().peek());

        taskList.start(newsletter, executor, ctx -> {
            ctx.setStatusText("Sending emails");
            executor.pause();
        });
        taskList.start(export, executor, ctx -> {
            ctx.setProgress(0.75);
            executor.pause();
            ctx.setProgress(1);
        });
        taskList.start(report, executor, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
        });
        assertEquals(Optional.empty(), taskList.overallProgress().peek(), "no work has reported Progress yet");

        executor.runNext(); // newsletter pauses without Progress
        assertEquals(Optional.empty(), taskList.overallProgress().peek());
        executor.resume(); // newsletter succeeds
        executor.runNext(); // export at 0.75
        assertEquals(Optional.of(0.75), taskList.overallProgress().peek());

        executor.resume(); // export succeeds: it no longer counts
        executor.runNext(); // report at 0.25
        assertEquals(Optional.of(0.25), taskList.overallProgress().peek());

        executor.resume();
        assertEquals(Optional.empty(), taskList.overallProgress().peek());
    }

    private PulseTask task(int index) {
        return taskList.tasks().peek().stream().toList().get(index);
    }
}
