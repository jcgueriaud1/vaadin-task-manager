package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;

/** The application starts a Task with its work and the executor that runs it. */
class TaskRunTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList();
    private final Action newsletter = new Action("Send newsletter", BlockingMode.NON_BLOCKING);
    private final Action report = new Action("Generate report", BlockingMode.NON_BLOCKING);

    @Test
    void startedTaskIsRunningUntilItsWorkReturnsThenSucceeded() {
        Task task = taskList.start(newsletter, executor, ctx -> {});

        assertEquals(List.of(task), taskList.tasks().peekValues().toList());
        assertEquals(newsletter, task.action());
        assertEquals(TaskState.RUNNING, task.state().peek());

        executor.runAll();

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
    }

    @Test
    void aTaskStaysRunningUntilTheExecutorRunsItsWork() {
        Task task = taskList.start(newsletter, executor, ctx -> {});

        assertEquals(TaskState.RUNNING, task.state().peek());
        assertTrue(taskList.isRunning(newsletter).peek());
    }

    @Test
    void statusTextFollowsWhatTheWorkReports() {
        Task task = taskList.start(newsletter, executor, ctx -> {
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
        Task task = taskList.start(newsletter, executor, ctx -> {
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
        Task task = taskList.start(newsletter, executor, ctx -> {
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
        Task task = taskList.start(report, executor, ctx -> {
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
        Task task = taskList.start(newsletter, executor, ctx -> {
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
        taskList.start(newsletter, executor, ctx -> {});
        assertTrue(taskList.isRunning(newsletter).peek());
        assertFalse(taskList.isRunning(report).peek());

        assertThrows(IllegalStateException.class, () -> taskList.start(newsletter, executor, ctx -> {}));
        taskList.start(report, executor, ctx -> {});
        assertEquals(2, taskList.tasks().peek().size());

        executor.runAll();

        assertFalse(taskList.isRunning(newsletter).peek());
        taskList.start(newsletter, executor, ctx -> {});
        assertEquals(3, taskList.tasks().peek().size());
    }

    @Test
    void actionsWithTheSameNameAndBlockingModeAreTheSameAction() {
        taskList.start(new Action("Send newsletter", BlockingMode.NON_BLOCKING), executor, ctx -> {});

        assertTrue(taskList.isRunning(newsletter).peek());
        assertThrows(IllegalStateException.class, () -> taskList.start(newsletter, executor, ctx -> {}));
    }

    @Test
    void aTaskIsIndeterminateUntilItsWorkReportsProgress() {
        Task task = taskList.start(report, executor, ctx -> {
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
        Task task = taskList.start(report, executor, ctx -> {
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
        Task task = taskList.start(newsletter, executor, ctx -> ctx.setResultSummary("1,240 emails sent"));
        assertEquals("", task.resultSummary().peek());

        executor.runAll();

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
        assertEquals("1,240 emails sent", task.resultSummary().peek());
    }

    @Test
    void workCanRunOnTheCallersThread() {
        Task task = taskList.start(newsletter, Runnable::run, ctx -> ctx.setResultSummary("1,240 emails sent"));

        assertEquals(TaskState.SUCCEEDED, task.state().peek());
        assertEquals("1,240 emails sent", task.resultSummary().peek());
    }

    @Test
    void aTaskCancelledBeforeTheExecutorRunsItEndsCancelledWithoutRunningItsWork() {
        List<String> ran = new ArrayList<>();
        Task task = taskList.start(newsletter, executor, ctx -> ran.add("work"));

        task.cancel();
        executor.runAll();

        assertEquals(List.of(), ran);
        assertEquals(TaskState.CANCELLED, task.state().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void workThatThrowsACheckedExceptionMakesTheTaskFailed() {
        Task task = taskList.start(newsletter, executor, ctx -> sneakyThrow(new java.io.IOException("Disk full")));

        executor.runAll();

        assertEquals(TaskState.FAILED, task.state().peek());
        assertEquals("Disk full", task.errorMessage().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void workThatThrowsAnErrorMakesTheTaskFailedAndTheErrorStillReachesTheThread() {
        Error error = assertThrows(Error.class, () -> taskList.start(newsletter, Runnable::run, ctx -> {
            throw new StackOverflowError("Too deep");
        }));

        assertEquals("Too deep", error.getMessage());
        Task task = taskList.tasks().peekValues().findFirst().orElseThrow();
        assertEquals(TaskState.FAILED, task.state().peek());
        assertEquals("Too deep", task.errorMessage().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void anExecutorThatRefusesTheWorkMakesTheTaskFailedInsteadOfLeavingItRunning() {
        Executor shutDown = command -> {
            throw new RejectedExecutionException("Executor is shut down");
        };

        Task task = taskList.start(newsletter, shutDown, ctx -> {});

        assertEquals(TaskState.FAILED, task.state().peek());
        assertEquals("Couldn't start: Executor is shut down", task.errorMessage().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void aTaskRunsItsWorkAtMostOnce() {
        Task task = taskList.start(newsletter, Runnable::run, ctx -> {});

        IllegalStateException again = assertThrows(IllegalStateException.class, () -> task.run(ctx -> {}));
        assertEquals("Task " + task.id() + " already ran", again.getMessage());
        assertEquals(TaskState.SUCCEEDED, task.state().peek());
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
