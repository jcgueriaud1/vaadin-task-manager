package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TaskListTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList();
    private final Action newsletter = new Action("Send newsletter", BlockingMode.NON_BLOCKING);
    private final Action report = new Action("Generate report", BlockingMode.NON_BLOCKING);
    private final Action sync = new Action("Sync with server", BlockingMode.NON_BLOCKING);
    private final Action export = new Action("Export data", BlockingMode.NON_BLOCKING);

    @Test
    void dismissedTaskLeavesTheTrayButStaysInTheTaskList() {
        Task sent = taskList.start(newsletter, executor, ctx -> {});
        Task generated = taskList.start(report, executor, ctx -> {});
        executor.runAll();

        taskList.dismiss(sent);

        assertEquals(List.of(generated), taskList.trayTasks().peek());
        assertEquals(List.of(sent, generated), taskList.tasks().peekValues().toList());
    }

    @Test
    void aRunningTaskCannotBeDismissed() {
        Task running = taskList.start(newsletter, executor, ctx -> {});

        assertThrows(IllegalStateException.class, () -> taskList.dismiss(running));
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
        Task failed = taskList.start(sync, executor, ctx -> {
            runs.add("sync");
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        assertTrue(failed.canRetry());

        Task returned = taskList.retry(failed);

        assertEquals(2, taskList.tasks().peek().size());
        Task retried = task(1);
        assertSame(retried, returned);
        assertEquals(sync, retried.action());
        assertEquals(TaskState.RUNNING, retried.state().peek());
        assertEquals(1, executor.queued(), "the retry waits on the same executor");
        executor.runAll();
        assertEquals(List.of("sync", "sync"), runs);
        assertEquals(TaskState.FAILED, retried.state().peek());
        assertEquals(TaskState.FAILED, failed.state().peek());
        assertEquals("Server unreachable", failed.errorMessage().peek());
    }

    @Test
    void aTaskStartedAsNeverRetryableCannotBeRetried() {
        Task failed = taskList.start(sync, executor, ctx -> {
            throw new IllegalStateException("Server unreachable");
        }, Retry.NEVER);
        executor.runAll();

        assertFalse(failed.canRetry());
        assertThrows(IllegalStateException.class, () -> taskList.retry(failed));
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void retryIsRefusedForASucceededTask() {
        Task succeeded = taskList.start(newsletter, Runnable::run, ctx -> {});

        assertThrows(IllegalStateException.class, () -> taskList.retry(succeeded));
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void retryIsRefusedWhileTheActionAlreadyHasARunningTask() {
        Task failed = taskList.start(sync, executor, ctx -> {
            executor.pause();
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        taskList.retry(failed);

        assertThrows(IllegalStateException.class, () -> taskList.retry(failed));
        assertEquals(2, taskList.tasks().peek().size());
    }

    @Test
    void clearFinishedRemovesEveryFinishedTaskAndKeepsTheRunningOnes() {
        taskList.start(newsletter, executor, ctx -> {});
        taskList.start(sync, executor, ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        Task running = taskList.start(export, executor, ctx -> executor.pause());
        executor.runNext();

        taskList.clearFinished();

        assertEquals(List.of(running), taskList.tasks().peekValues().toList());
    }

    @Test
    void theTaskListKeepsAtMostFiftyTasksEvictingTheOldestFinishedFirst() {
        Task running = taskList.start(report, executor, ctx -> {});
        for (int i = 0; i < 49; i++) {
            // Run on the test's thread: any Executor will do
            taskList.start(newsletter, Runnable::run, ctx -> {});
        }
        List<Task> before = taskList.tasks().peekValues().toList();
        Task oldestFinished = before.get(1);
        Task secondOldestFinished = before.get(2);
        assertEquals(50, before.size());

        Task newest = taskList.start(sync, executor, ctx -> {});

        List<Task> tasks = taskList.tasks().peekValues().toList();
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



    private Task task(int index) {
        return taskList.tasks().peekValues().toList().get(index);
    }
}
