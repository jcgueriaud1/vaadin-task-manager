package com.example.taskmanager.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * The application starts it with {@link TaskList#start(Action, java.util.concurrent.Executor, Consumer)},
 * which runs its work.
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
    private final AtomicBoolean ran = new AtomicBoolean();

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

    /** How long a Running Task has run until {@code now}, or how long a Finished Task took. */
    public Signal<Duration> elapsed(Signal<Instant> now) {
        return Signal.computed(() -> {
            if (state.get() == TaskState.RUNNING) {
                return Duration.between(startedAt, now.get());
            }
            return Duration.between(startedAt, finishedAt);
        });
    }

    /**
     * A straight-line estimate of how long a Running Task still needs, from its
     * Progress so far. Empty unless the Task is determinate and above 5%, so a
     * Finished Task has none.
     */
    public Signal<Optional<Duration>> timeLeft(Signal<Instant> now) {
        return Signal.computed(() -> {
            double done = progress.get();
            if (!determinate().get() || done <= MIN_PROGRESS_FOR_ESTIMATE) {
                return Optional.empty();
            }
            long elapsedMillis = Duration.between(startedAt, now.get()).toMillis();
            return Optional.of(Duration.ofMillis(Math.round(elapsedMillis * (1 - done) / done)));
        });
    }

    /** The Task state. Views use it to filter Tasks; what a state means is in the signals below. */
    public Signal<TaskState> state() {
        return state.asReadonly();
    }

    /** Whether the Task is Running. */
    public Signal<Boolean> running() {
        return state.map(s -> s == TaskState.RUNNING);
    }

    public Signal<String> statusText() {
        return statusText.asReadonly();
    }

    /** How much of the Task is done, from 0 to 1. Only meaningful while it is {@link #determinate()}. */
    public Signal<Double> progress() {
        return progress.asReadonly();
    }

    /**
     * Whether the Task is Running and its work has reported Progress. Until it
     * has, or once it is Finished, the Task shows as indeterminate.
     */
    public Signal<Boolean> determinate() {
        return Signal.computed(() -> state.get() == TaskState.RUNNING && reportsProgress.get());
    }

    /** How the Task ended, or empty while it is Running. */
    public Signal<Optional<Outcome>> outcome() {
        return Signal.computed(() -> switch (state.get()) {
            case RUNNING -> Optional.empty();
            case SUCCEEDED -> Optional.of(new Outcome.Succeeded(resultSummary.get()));
            case FAILED -> Optional.of(new Outcome.Failed(errorMessage.get()));
            case CANCELLED -> Optional.of(new Outcome.Cancelled());
        });
    }

    /** Whether the user can Retry the Task: it is Failed or Cancelled, and wasn't started with {@link Retry#NEVER}. */
    public Signal<Boolean> retryable() {
        return state.map(s -> retry != null && (s == TaskState.FAILED || s == TaskState.CANCELLED));
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
     * Runs the work on the calling thread, once, and ends the Task with its
     * outcome: Cancelled without running the work if the user already cancelled
     * it, Succeeded if the work returns, Cancelled if the user cancelled it
     * meanwhile, and Failed, with the message, if it throws. An {@link Error} is
     * rethrown after the Task ends Failed.
     *
     * @throws IllegalStateException if the Task already ran
     */
    void run(Consumer<? super TaskContext> work) {
        if (!ran.compareAndSet(false, true)) {
            throw new IllegalStateException("Task " + id + " already ran");
        }
        try {
            if (cancelled) {
                markCancelled();
                return;
            }
            TaskContext context = context();
            work.accept(context);
            if (context.isCancelled()) {
                throw new CancellationException();
            }
            succeed();
        } catch (CancellationException e) {
            markCancelled();
        } catch (Exception e) {
            log.error("Task of {} failed", action.name(), e);
            fail(messageOf(e));
        } catch (Error e) {
            log.error("Task of {} failed", action.name(), e);
            fail(messageOf(e));
            throw e;
        } finally {
            taskList.finished(this);
        }
    }

    /** The executor refused the work: the Task ends Failed without running it. */
    void couldNotStart(Exception e) {
        if (ran.compareAndSet(false, true)) {
            log.error("Task of {} couldn't start", action.name(), e);
            fail("Couldn't start: " + messageOf(e));
            taskList.finished(this);
        }
    }

    private static String messageOf(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Starts a new Task with the same work on the same executor. */
    Task retry() {
        if (!retryable().peek()) {
            throw new IllegalStateException("Task " + id + " can't be retried");
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
