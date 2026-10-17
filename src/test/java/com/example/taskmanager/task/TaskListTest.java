package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
        Task sent = start(newsletter, ctx -> {});
        Task generated = start(report, ctx -> {});
        executor.runAll();

        taskList.dismiss(sent);

        assertEquals(List.of(generated), taskList.trayTasks().peek());
        assertEquals(List.of(sent, generated), taskList.tasks().peekValues().toList());
    }

    @Test
    void aRunningTaskCannotBeDismissed() {
        Task running = taskList.start(newsletter);

        assertThrows(IllegalStateException.class, () -> taskList.dismiss(running));
        assertEquals(List.of(running), taskList.trayTasks().peek());
    }

    @Test
    void runningCountFollowsTasksStartingAndFinishing() {
        assertEquals(0, taskList.runningCount().peek());

        start(newsletter, ctx -> {});
        start(report, ctx -> {});
        assertEquals(2, taskList.runningCount().peek());

        executor.runNext();
        assertEquals(1, taskList.runningCount().peek());

        executor.runAll();
        assertEquals(0, taskList.runningCount().peek());
    }

    @Test
    void retryStartsANewTaskThroughItsCallbackAndLeavesTheOldOneAsItWas() {
        Task failed = startSyncFailing(ctx -> {});
        executor.runAll();
        assertTrue(failed.canRetry());

        Task returned = taskList.retry(failed);

        assertEquals(2, taskList.tasks().peek().size());
        Task retried = task(1);
        assertSame(retried, returned);
        assertEquals(sync, retried.action());
        assertEquals(TaskState.RUNNING, retried.state().peek());
        assertEquals(TaskState.FAILED, failed.state().peek());
        assertEquals("Server unreachable", failed.errorMessage().peek());
    }

    @Test
    void aTaskStartedWithoutACallbackCannotBeRetried() {
        Task failed = start(sync, ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();

        assertFalse(failed.canRetry());
        assertThrows(IllegalStateException.class, () -> taskList.retry(failed));
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void retryIsRefusedForASucceededTask() {
        Task succeeded = taskList.start(newsletter, () -> taskList.start(newsletter));
        succeeded.run(ctx -> {});

        assertThrows(IllegalStateException.class, () -> taskList.retry(succeeded));
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void retryIsRefusedWhileTheActionAlreadyHasARunningTask() {
        Task failed = startSyncFailing(ctx -> executor.pause());
        executor.runAll();
        taskList.retry(failed);

        assertThrows(IllegalStateException.class, () -> taskList.retry(failed));
        assertEquals(2, taskList.tasks().peek().size());
    }

    @Test
    void clearFinishedRemovesEveryFinishedTaskAndKeepsTheRunningOnes() {
        start(newsletter, ctx -> {});
        start(sync, ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        executor.runAll();
        Task running = start(export, ctx -> executor.pause());
        executor.runNext();

        taskList.clearFinished();

        assertEquals(List.of(running), taskList.tasks().peekValues().toList());
    }

    @Test
    void theTaskListKeepsAtMostFiftyTasksEvictingTheOldestFinishedFirst() {
        Task running = taskList.start(report);
        for (int i = 0; i < 49; i++) {
            // Run on the test's thread: the Task list doesn't care where work runs
            taskList.start(newsletter).run(ctx -> {});
        }
        List<Task> before = taskList.tasks().peekValues().toList();
        Task oldestFinished = before.get(1);
        Task secondOldestFinished = before.get(2);
        assertEquals(50, before.size());

        Task newest = taskList.start(sync);

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

        start(newsletter, ctx -> {
            ctx.setStatusText("Sending emails");
            executor.pause();
        });
        start(export, ctx -> {
            ctx.setProgress(0.75);
            executor.pause();
            ctx.setProgress(1);
        });
        start(report, ctx -> {
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

    private Task start(Action action, Consumer<TaskContext> work) {
        Task task = taskList.start(action);
        executor.execute(() -> task.run(work));
        return task;
    }

    /** Starts Sync with server, which fails after the given step, and can be Retried the same way. */
    private Task startSyncFailing(Consumer<TaskContext> step) {
        Task task = taskList.start(sync, () -> startSyncFailing(step));
        executor.execute(() -> task.run(ctx -> {
            step.accept(ctx);
            throw new IllegalStateException("Server unreachable");
        }));
        return task;
    }

    private Task task(int index) {
        return taskList.tasks().peekValues().toList().get(index);
    }
}
