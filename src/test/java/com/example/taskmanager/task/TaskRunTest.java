package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/** The application starts a Task in the Task list and runs its work on a thread of its own choosing. */
class TaskRunTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList();
    private final Action newsletter = new Action("Send newsletter", BlockingMode.NON_BLOCKING);
    private final Action report = new Action("Generate report", BlockingMode.NON_BLOCKING);

    @Test
    void startedTaskIsRunningUntilItsWorkReturnsThenSucceeded() {
        Task task = start(newsletter, ctx -> {});

        assertEquals(List.of(task), taskList.tasks().peekValues().toList());
        assertEquals(newsletter, task.action());
        assertEquals(TaskState.RUNNING, task.state().peek());

        executor.runAll();

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
    }

    @Test
    void aTaskWhoseWorkNeverRunsStaysRunning() {
        Task task = taskList.start(newsletter);

        assertEquals(TaskState.RUNNING, task.state().peek());
        assertTrue(taskList.isRunning(newsletter).peek());
    }

    @Test
    void statusTextFollowsWhatTheWorkReports() {
        Task task = start(newsletter, ctx -> {
            ctx.setStatusText("Preparing recipients");
            executor.pause();
            ctx.setStatusText("Sending emails");
            executor.pause();
        });
        assertEquals("", task.statusText().peek());

        executor.runNext();
        assertEquals("Preparing recipients", task.statusText().peek());

        executor.resume();
        assertEquals("Sending emails", task.statusText().peek());
        assertEquals(TaskState.RUNNING, task.state().peek());
    }

    @Test
    void cancelledTaskStopsAtItsNextStatusTextAndEndsCancelled() {
        List<String> reachedSteps = new ArrayList<>();
        Task task = start(newsletter, ctx -> {
            ctx.setStatusText("Preparing recipients");
            executor.pause();
            ctx.setStatusText("Sending emails");
            reachedSteps.add("Sending emails");
        });
        executor.runNext();

        task.cancel();
        executor.resume();

        assertEquals(List.of(), reachedSteps);
        assertEquals(TaskState.CANCELLED, task.state().peek());
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void workThatChecksIsCancelledCanStopQuietlyAndTheTaskStillEndsCancelled() {
        List<Boolean> seen = new ArrayList<>();
        Task task = start(newsletter, ctx -> {
            seen.add(ctx.isCancelled());
            executor.pause();
            seen.add(ctx.isCancelled());
        });
        executor.runNext();

        task.cancel();
        executor.resume();

        assertEquals(List.of(false, true), seen);
        assertEquals(TaskState.CANCELLED, task.state().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void cancelledTaskStopsAtItsNextProgressAndEndsCancelled() {
        Task task = start(report, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(0.5);
        });
        executor.runNext();

        task.cancel();
        executor.resume();

        assertEquals(0.25, task.progress().peek());
        assertEquals(TaskState.CANCELLED, task.state().peek());
    }

    @Test
    void workThatThrowsMakesTheTaskFailedWithTheExceptionsMessage() {
        Task task = start(newsletter, ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        assertEquals("", task.errorMessage().peek());

        executor.runAll();

        assertEquals(TaskState.FAILED, task.state().peek());
        assertEquals("Server unreachable", task.errorMessage().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void anActionWithARunningTaskRefusesToStartAgainUntilItFinishes() {
        start(newsletter, ctx -> {});
        assertTrue(taskList.isRunning(newsletter).peek());
        assertFalse(taskList.isRunning(report).peek());

        assertThrows(IllegalStateException.class, () -> taskList.start(newsletter));
        start(report, ctx -> {});
        assertEquals(2, taskList.tasks().peek().size());

        executor.runAll();

        assertFalse(taskList.isRunning(newsletter).peek());
        start(newsletter, ctx -> {});
        assertEquals(3, taskList.tasks().peek().size());
    }

    @Test
    void actionsWithTheSameNameAndBlockingModeAreTheSameAction() {
        taskList.start(new Action("Send newsletter", BlockingMode.NON_BLOCKING));

        assertTrue(taskList.isRunning(newsletter).peek());
        assertThrows(IllegalStateException.class, () -> taskList.start(newsletter));
    }

    @Test
    void aTaskIsIndeterminateUntilItsWorkReportsProgress() {
        Task task = start(report, ctx -> {
            ctx.setStatusText("Collecting data");
            executor.pause();
            ctx.setProgress(0.25);
            executor.pause();
        });
        executor.runNext();
        assertFalse(task.reportsProgress().peek());

        executor.resume();
        assertTrue(task.reportsProgress().peek());
    }

    @Test
    void progressFollowsWhatTheWorkReportsClampedToZeroToOne() {
        Task task = start(report, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(1.5);
            executor.pause();
            ctx.setProgress(-0.5);
            executor.pause();
        });
        assertEquals(0.0, task.progress().peek());

        executor.runNext();
        assertEquals(0.25, task.progress().peek());

        executor.resume();
        assertEquals(1.0, task.progress().peek());

        executor.resume();
        assertEquals(0.0, task.progress().peek());
    }

    @Test
    void aTaskCanEndWithAResultSummary() {
        Task task = start(newsletter, ctx -> ctx.setResultSummary("1,240 emails sent"));
        assertEquals("", task.resultSummary().peek());

        executor.runAll();

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
        assertEquals("1,240 emails sent", task.resultSummary().peek());
    }

    private Task start(Action action, Consumer<TaskContext> work) {
        Task task = taskList.start(action);
        executor.execute(() -> task.run(work));
        return task;
    }
}
