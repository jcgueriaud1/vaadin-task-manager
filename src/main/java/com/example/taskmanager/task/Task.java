package com.example.taskmanager.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/**
 * One run of an Action. Its fixed fields never change; the rest are signals,
 * which every view follows: the tray, the task center, the Background tasks
 * page and the {@code BlockingTaskDialog}.
 * <p>
 * The application runs the work itself, on any thread, by passing it to
 * {@link #run(Consumer)}.
 */
public final class Task {

    private static final Logger log = LoggerFactory.getLogger(Task.class);

    private static final double MIN_PROGRESS_FOR_ESTIMATE = 0.05;

    private final long id;
    private final Action action;
    private final TaskList taskList;
    private final Supplier<Task> retry;
    private final ValueSignal<TaskState> state = new ValueSignal<>(TaskState.RUNNING);
    private final ValueSignal<String> statusText = new ValueSignal<>("");
    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<Boolean> reportsProgress = new ValueSignal<>(false);
    private final ValueSignal<String> resultSummary = new ValueSignal<>("");
    private final ValueSignal<String> errorMessage = new ValueSignal<>("");
    private final ValueSignal<Boolean> dismissed = new ValueSignal<>(false);
    private final ValueSignal<Boolean> unseen = new ValueSignal<>(false);
    private final Clock clock;
    private final Instant startedAt;
    private volatile Instant finishedAt;
    private volatile boolean cancelled;

    Task(long id, Action action, TaskList taskList, Supplier<Task> retry, Clock clock) {
        this.id = id;
        this.action = action;
        this.taskList = taskList;
        this.retry = retry;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    /** Identifies the Task within its Task list, for example in a URL. */
    public long id() {
        return id;
    }

    public Action action() {
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
     * Progress so far. Empty unless its work reports Progress and it is above 5%.
     */
    public Optional<Duration> timeLeft(Instant now) {
        double done = progress.peek();
        if (!reportsProgress.peek() || state.peek() != TaskState.RUNNING || done <= MIN_PROGRESS_FOR_ESTIMATE) {
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

    /** How much of the Task is done, from 0 to 1. Only meaningful once it {@link #reportsProgress()}. */
    public Signal<Double> progress() {
        return progress.asReadonly();
    }

    /** Whether the work has reported Progress yet. Until it does, the Task shows as indeterminate. */
    public Signal<Boolean> reportsProgress() {
        return reportsProgress.asReadonly();
    }

    /** Whether whoever started the Task said how to Retry it. */
    public boolean canRetry() {
        return retry != null;
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

    /** Whether this Finished Task is Unseen: no tab of the session was visible when it finished. */
    public Signal<Boolean> unseen() {
        return unseen.asReadonly();
    }

    /**
     * Asks the Task to stop. The work stops the next time it reports, and the
     * Task then becomes Cancelled.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Runs the work on the calling thread and ends the Task with its outcome:
     * Succeeded if it returns, Cancelled if the user cancelled it, and Failed,
     * with the exception's message, if it throws. Call it from the thread the
     * application chose, for example {@code Thread.startVirtualThread(() -> task.run(work))}
     * or an {@code @Async} method. It never throws: a failure is logged.
     */
    public void run(Consumer<? super TaskContext> work) {
        try {
            TaskContext context = context();
            work.accept(context);
            if (context.isCancelled()) {
                throw new CancellationException();
            }
            succeed();
        } catch (CancellationException e) {
            markCancelled();
        } catch (RuntimeException e) {
            log.error("Task of {} failed", action.name(), e);
            fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        taskList.finished(this);
    }

    /** Starts a new Task through the callback given when this one was started. */
    Task retry() {
        if (retry == null) {
            throw new IllegalStateException("Whoever started this Task didn't say how to Retry it");
        }
        return retry.get();
    }

    private TaskContext context() {
        return new TaskContext() {
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
                reportsProgress.set(true);
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

    private void succeed() {
        finishedAt = clock.instant();
        state.set(TaskState.SUCCEEDED);
    }

    private void fail(String message) {
        finishedAt = clock.instant();
        errorMessage.set(message);
        state.set(TaskState.FAILED);
    }

    private void markCancelled() {
        finishedAt = clock.instant();
        state.set(TaskState.CANCELLED);
    }

    void dismiss() {
        dismissed.set(true);
    }

    void markUnseen(boolean value) {
        unseen.set(value);
    }
}
