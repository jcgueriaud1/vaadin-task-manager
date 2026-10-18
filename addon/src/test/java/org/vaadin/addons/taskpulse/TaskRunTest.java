package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;

/** The application starts a Task with its work and the executor that runs it. */
class TaskRunTest {

    private final TestExecutor executor = new TestExecutor();
    private final PulseTaskList taskList = new PulseTaskList();
    private final PulseAction newsletter = new PulseAction("Send newsletter", BlockingMode.NON_BLOCKING);
    private final PulseAction report = new PulseAction("Generate report", BlockingMode.NON_BLOCKING);

    @Test
    void startedTaskIsRunningUntilItsWorkReturnsThenSucceeded() {
        PulseTask task = taskList.start(newsletter, executor, ctx -> {});

        assertEquals(List.of(task), taskList.tasks().peekValues().toList());
        assertEquals(newsletter, task.action());
        assertEquals(PulseTask.State.RUNNING, task.state().peek());

        executor.runAll();

        assertEquals(PulseTask.State.SUCCEEDED, task.state().peek());
    }

    @Test
    void aTaskStaysRunningUntilTheExecutorRunsItsWork() {
        PulseTask task = taskList.start(newsletter, executor, ctx -> {});

        assertEquals(PulseTask.State.RUNNING, task.state().peek());
        assertTrue(taskList.isRunning(newsletter).peek());
    }

    @Test
    void statusTextFollowsWhatTheWorkReports() {
        PulseTask task = taskList.start(newsletter, executor, ctx -> {
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
        assertEquals(PulseTask.State.RUNNING, task.state().peek());
    }

    @Test
    void cancelledTaskStopsAtItsNextStatusTextAndEndsCancelled() {
        List<String> reachedSteps = new ArrayList<>();
        PulseTask task = taskList.start(newsletter, executor, ctx -> {
            ctx.setStatusText("Preparing recipients");
            executor.pause();
            ctx.setStatusText("Sending emails");
            reachedSteps.add("Sending emails");
        });
        executor.runNext();

        task.cancel();
        executor.resume();

        assertEquals(List.of(), reachedSteps);
        assertEquals(PulseTask.State.CANCELLED, task.state().peek());
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void workThatChecksIsCancelledCanStopQuietlyAndTheTaskStillEndsCancelled() {
        List<Boolean> seen = new ArrayList<>();
        PulseTask task = taskList.start(newsletter, executor, ctx -> {
            seen.add(ctx.isCancelled());
            executor.pause();
            seen.add(ctx.isCancelled());
        });
        executor.runNext();

        task.cancel();
        executor.resume();

        assertEquals(List.of(false, true), seen);
        assertEquals(PulseTask.State.CANCELLED, task.state().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void cancelledTaskStopsAtItsNextProgressAndEndsCancelled() {
        PulseTask task = taskList.start(report, executor, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(0.5);
        });
        executor.runNext();

        task.cancel();
        executor.resume();

        assertEquals(0.25, task.progress().peek());
        assertEquals(PulseTask.State.CANCELLED, task.state().peek());
    }

    @Test
    void workThatThrowsMakesTheTaskFailedWithTheExceptionsMessage() {
        PulseTask task = taskList.start(newsletter, executor, ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        assertEquals(Optional.empty(), task.outcome().peek());

        executor.runAll();

        assertEquals(PulseTask.State.FAILED, task.state().peek());
        assertEquals(Optional.of(new Outcome.Failed("Server unreachable")), task.outcome().peek());
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
        taskList.start(new PulseAction("Send newsletter", BlockingMode.NON_BLOCKING), executor, ctx -> {});

        assertTrue(taskList.isRunning(newsletter).peek());
        assertThrows(IllegalStateException.class, () -> taskList.start(newsletter, executor, ctx -> {}));
    }

    @Test
    void aTaskIsIndeterminateUntilItsWorkReportsProgress() {
        PulseTask task = taskList.start(report, executor, ctx -> {
            ctx.setStatusText("Collecting data");
            executor.pause();
            ctx.setProgress(0.25);
            executor.pause();
        });
        executor.runNext();
        assertFalse(task.determinate().peek());

        executor.resume();
        assertTrue(task.determinate().peek());
    }

    @Test
    void progressFollowsWhatTheWorkReportsClampedToZeroToOne() {
        PulseTask task = taskList.start(report, executor, ctx -> {
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
        PulseTask task = taskList.start(newsletter, executor, ctx -> ctx.setResultSummary("1,240 emails sent"));
        assertEquals(Optional.empty(), task.outcome().peek());

        executor.runAll();

        assertEquals(PulseTask.State.SUCCEEDED, task.state().peek());
        assertEquals(Optional.of(new Outcome.Succeeded("1,240 emails sent")), task.outcome().peek());
    }

    @Test
    void workCanRunOnTheCallersThread() {
        PulseTask task = taskList.start(newsletter, Runnable::run, ctx -> ctx.setResultSummary("1,240 emails sent"));

        assertEquals(PulseTask.State.SUCCEEDED, task.state().peek());
        assertEquals(Optional.of(new Outcome.Succeeded("1,240 emails sent")), task.outcome().peek());
    }

    @Test
    void aTaskCancelledBeforeTheExecutorRunsItEndsCancelledWithoutRunningItsWork() {
        List<String> ran = new ArrayList<>();
        PulseTask task = taskList.start(newsletter, executor, ctx -> ran.add("work"));

        task.cancel();
        executor.runAll();

        assertEquals(List.of(), ran);
        assertEquals(PulseTask.State.CANCELLED, task.state().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void workThatThrowsACheckedExceptionMakesTheTaskFailed() {
        PulseTask task = taskList.start(newsletter, executor, ctx -> sneakyThrow(new java.io.IOException("Disk full")));

        executor.runAll();

        assertEquals(PulseTask.State.FAILED, task.state().peek());
        assertEquals(Optional.of(new Outcome.Failed("Disk full")), task.outcome().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void workThatThrowsAnErrorMakesTheTaskFailedAndTheErrorStillReachesTheThread() {
        Error error = assertThrows(Error.class, () -> taskList.start(newsletter, Runnable::run, ctx -> {
            throw new StackOverflowError("Too deep");
        }));

        assertEquals("Too deep", error.getMessage());
        PulseTask task = taskList.tasks().peekValues().findFirst().orElseThrow();
        assertEquals(PulseTask.State.FAILED, task.state().peek());
        assertEquals(Optional.of(new Outcome.Failed("Too deep")), task.outcome().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void anExecutorThatRefusesTheWorkMakesTheTaskFailedInsteadOfLeavingItRunning() {
        Executor shutDown = command -> {
            throw new RejectedExecutionException("Executor is shut down");
        };

        PulseTask task = taskList.start(newsletter, shutDown, ctx -> {});

        assertEquals(PulseTask.State.FAILED, task.state().peek());
        assertEquals(Optional.of(new Outcome.Failed("Couldn't start: Executor is shut down")), task.outcome().peek());
        assertFalse(taskList.isRunning(newsletter).peek());
    }

    @Test
    void aTaskRunsItsWorkAtMostOnce() {
        PulseTask task = taskList.start(newsletter, Runnable::run, ctx -> {});

        IllegalStateException again = assertThrows(IllegalStateException.class, () -> task.run(ctx -> {}));
        assertEquals("Task " + task.id() + " already ran", again.getMessage());
        assertEquals(PulseTask.State.SUCCEEDED, task.state().peek());
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @Test
    void withoutAnExecutorTheWorkRunsOnAVirtualThread() throws Exception {
        CompletableFuture<Thread> ranOn = new CompletableFuture<>();
        PulseTask task = new PulseTaskList().start(newsletter, ctx -> ranOn.complete(Thread.currentThread()));

        assertTrue(ranOn.get(5, TimeUnit.SECONDS).isVirtual());
        while (task.running().peek()) {
            Thread.onSpinWait();
        }
        assertEquals(PulseTask.State.SUCCEEDED, task.state().peek());
    }
}
