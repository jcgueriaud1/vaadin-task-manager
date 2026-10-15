package com.example.taskmanager.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/** One run of an Action. Its fixed fields never change; the rest are signals. */
public final class Task {

    private static final double MIN_PROGRESS_FOR_ESTIMATE = 0.05;

    private final AsyncAction<?> action;
    private final ValueSignal<TaskState> state = new ValueSignal<>(TaskState.RUNNING);
    private final ValueSignal<String> statusText = new ValueSignal<>("");
    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<String> resultSummary = new ValueSignal<>("");
    private final ValueSignal<String> errorMessage = new ValueSignal<>("");
    private final ValueSignal<Boolean> dismissed = new ValueSignal<>(false);
    private final Clock clock;
    private final Instant startedAt;
    private volatile Instant finishedAt;
    private volatile boolean cancelled;

    Task(AsyncAction<?> action, Clock clock) {
        this.action = action;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public AsyncAction<?> action() {
        return action;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** How long a Running Task has run until now, or how long a Finished Task took. */
    public Duration elapsed(Instant now) {
        Instant end = finishedAt;
        return Duration.between(startedAt, end != null ? end : now);
    }

    /**
     * A straight-line estimate of how long a Running Task still needs, from its
     * Progress so far. Empty unless its Action reports Progress and it is above 5%.
     */
    public Optional<Duration> timeLeft(Instant now) {
        double done = progress.peek();
        if (!action.reportsProgress() || state.peek() != TaskState.RUNNING || done <= MIN_PROGRESS_FOR_ESTIMATE) {
            return Optional.empty();
        }
        long elapsedMillis = elapsed(now).toMillis();
        return Optional.of(Duration.ofMillis(Math.round(elapsedMillis * (1 - done) / done)));
    }

    public Signal<TaskState> state() {
        return state.asReadonly();
    }

    public Signal<String> statusText() {
        return statusText.asReadonly();
    }

    /** How much of the Task is done, from 0 to 1. Only meaningful if its Action reports Progress. */
    public Signal<Double> progress() {
        return progress.asReadonly();
    }

    /** What a Succeeded Task produced, or empty if its Action didn't say. */
    public Signal<String> resultSummary() {
        return resultSummary.asReadonly();
    }

    /** The message of the exception a Failed Task ended with, empty otherwise. */
    public Signal<String> errorMessage() {
        return errorMessage.asReadonly();
    }

    /** Whether the user has removed this Finished Task from the tray. */
    public Signal<Boolean> dismissed() {
        return dismissed.asReadonly();
    }

    /**
     * Asks the Task to stop. The work stops the next time it reports, and the
     * Task then becomes Cancelled.
     */
    public void cancel() {
        cancelled = true;
    }

    ProgressContext context() {
        return new ProgressContext() {
            @Override
            public void setStatusText(String text) {
                throwIfCancelled();
                statusText.set(text);
            }

            @Override
            public void setResultSummary(String text) {
                throwIfCancelled();
                resultSummary.set(text);
            }

            @Override
            public void setProgress(double value) {
                throwIfCancelled();
                progress.set(Math.clamp(value, 0.0, 1.0));
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }

    private void throwIfCancelled() {
        if (cancelled) {
            throw new CancellationException();
        }
    }

    void succeed() {
        finishedAt = clock.instant();
        state.set(TaskState.SUCCEEDED);
    }

    void fail(String message) {
        finishedAt = clock.instant();
        errorMessage.set(message);
        state.set(TaskState.FAILED);
    }

    void markCancelled() {
        finishedAt = clock.instant();
        state.set(TaskState.CANCELLED);
    }

    void dismiss() {
        dismissed.set(true);
    }
}
