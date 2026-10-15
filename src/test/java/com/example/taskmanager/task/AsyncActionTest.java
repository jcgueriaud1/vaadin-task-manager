package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

class AsyncActionTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList(executor);

    @Test
    void startedTaskIsRunningThenSucceedsWithTheWorksValue() {
        var action = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> 42);

        CompletableFuture<Integer> future = action.start(taskList);

        Task task = taskList.tasks().peekValues().findFirst().orElseThrow();
        assertSame(action, task.action());
        assertEquals(TaskState.RUNNING, task.state().peek());
        assertFalse(future.isDone());

        executor.runAll();

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
        assertEquals(42, future.join());
    }

    @Test
    void statusTextFollowsWhatTheWorkReports() {
        var action = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setStatusText("Preparing recipients");
            executor.pause();
            ctx.setStatusText("Sending emails");
            executor.pause();
            return null;
        });
        action.start(taskList);
        Task task = taskList.tasks().peekValues().findFirst().orElseThrow();
        assertEquals("", task.statusText().peek());

        executor.runNext();
        assertEquals("Preparing recipients", task.statusText().peek());

        executor.resume();
        assertEquals("Sending emails", task.statusText().peek());
        assertEquals(TaskState.RUNNING, task.state().peek());
    }

    @Test
    void anActionWithARunningTaskRefusesToStartAgainUntilItFinishes() {
        var action = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> "sent");
        var other = new AsyncAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> "report");

        action.start(taskList);
        assertTrue(taskList.isRunning(action).peek());
        assertFalse(taskList.isRunning(other).peek());

        assertThrows(IllegalStateException.class, () -> action.start(taskList));
        other.start(taskList);
        assertEquals(2, taskList.tasks().peek().size());

        executor.runAll();

        assertFalse(taskList.isRunning(action).peek());
        action.start(taskList);
        assertEquals(3, taskList.tasks().peek().size());
    }

    @Test
    void progressFollowsWhatTheWorkReportsClampedToZeroToOne() {
        var action = new AsyncProgressAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(1.5);
            executor.pause();
            ctx.setProgress(-0.5);
            executor.pause();
            return null;
        });
        assertTrue(action.reportsProgress());
        action.start(taskList);
        Task task = taskList.tasks().peekValues().findFirst().orElseThrow();
        assertEquals(0.0, task.progress().peek());

        executor.runNext();
        assertEquals(0.25, task.progress().peek());

        executor.resume();
        assertEquals(1.0, task.progress().peek());

        executor.resume();
        assertEquals(0.0, task.progress().peek());
    }

    @Test
    void anActionWithoutProgressCanStillEndWithAResultSummary() {
        var action = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setResultSummary("1,240 emails sent");
            return null;
        });
        assertFalse(action.reportsProgress());
        action.start(taskList);
        Task task = taskList.tasks().peekValues().findFirst().orElseThrow();
        assertEquals("", task.resultSummary().peek());

        executor.runAll();

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
        assertEquals("1,240 emails sent", task.resultSummary().peek());
    }

    @Test
    void anActionWithProgressCanEndWithAResultSummary() {
        var action = new AsyncProgressAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setProgress(1);
            ctx.setResultSummary("5 PDF files");
            return null;
        });
        action.start(taskList);
        Task task = taskList.tasks().peekValues().findFirst().orElseThrow();

        executor.runAll();

        assertEquals("5 PDF files", task.resultSummary().peek());
    }
}
