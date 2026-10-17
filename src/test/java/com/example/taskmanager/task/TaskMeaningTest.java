package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** What a Task says about itself, so that no view has to work it out from its Task state. */
class TaskMeaningTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList();
    private final Action report = new Action("Generate report", BlockingMode.NON_BLOCKING);

    @Test
    void aTaskIsDeterminateOnlyWhileRunningAndOnceItsWorkReportsProgress() {
        Task task = taskList.start(report, executor, ctx -> {
            executor.pause();
            ctx.setProgress(0.25);
            executor.pause();
        });
        assertTrue(task.running().peek());
        assertFalse(task.determinate().peek());

        executor.runNext();
        assertFalse(task.determinate().peek(), "no Progress reported yet");

        executor.resume();
        assertTrue(task.determinate().peek());

        executor.resume();
        assertFalse(task.running().peek());
        assertFalse(task.determinate().peek(), "a Finished Task is never determinate");
    }

    @Test
    void theOutcomeIsEmptyWhileRunningThenSucceededWithTheResultSummary() {
        Task task = taskList.start(report, executor, ctx -> {
            ctx.setResultSummary("5 PDF files");
            executor.pause();
        });
        executor.runNext();
        assertEquals(Optional.empty(), task.outcome().peek(), "no Outcome while Running");

        executor.resume();

        assertEquals(Optional.of(new Outcome.Succeeded("5 PDF files")), task.outcome().peek());
    }

    @Test
    void aSucceededTaskWithoutAResultSummaryHasAnEmptyOne() {
        Task task = taskList.start(report, Runnable::run, ctx -> {});

        assertEquals(Optional.of(new Outcome.Succeeded("")), task.outcome().peek());
    }

    @Test
    void aFailedTaskHasItsErrorAndNoResultSummary() {
        Task task = taskList.start(report, Runnable::run, ctx -> {
            ctx.setResultSummary("2 of 5 PDF files");
            throw new IllegalStateException("Disk full");
        });

        assertEquals(Optional.of(new Outcome.Failed("Disk full")), task.outcome().peek());
    }

    @Test
    void aCancelledTaskHasTheCancelledOutcome() {
        Task task = taskList.start(report, executor, ctx -> {});
        task.cancel();
        executor.runAll();

        assertEquals(Optional.of(new Outcome.Cancelled()), task.outcome().peek());
    }

    @Test
    void onlyAFailedOrCancelledTaskIsRetryable() {
        Task running = taskList.start(report, executor, ctx -> executor.pause());
        assertFalse(running.retryable().peek(), "Running");
        executor.runAll();
        assertFalse(running.retryable().peek(), "Succeeded");

        Task failed = taskList.start(report, Runnable::run, ctx -> {
            throw new IllegalStateException("Disk full");
        });
        assertTrue(failed.retryable().peek(), "Failed");

        Task cancelled = taskList.start(new Action("Export data", BlockingMode.NON_BLOCKING), executor, ctx -> {});
        cancelled.cancel();
        executor.runAll();
        assertTrue(cancelled.retryable().peek(), "Cancelled");
    }

    @Test
    void aTaskStartedAsNeverRetryableIsNotRetryableAndTheTaskListAgrees() {
        Task failed = taskList.start(report, Runnable::run, ctx -> {
            throw new IllegalStateException("Card declined");
        }, Retry.NEVER);

        assertFalse(failed.retryable().peek());
        assertThrows(IllegalStateException.class, () -> taskList.retry(failed));
    }
}
