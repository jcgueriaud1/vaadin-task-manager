package com.example.taskmanager.task;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An Action: something the user can start, defined once from a
 * name, a blocking mode and a work function. This one has no Progress; see
 * {@link AsyncProgressAction} for one that has.
 *
 * @param <T> the type of value the work produces
 */
public class AsyncAction<T> {

    private static final Logger log = LoggerFactory.getLogger(AsyncAction.class);

    private final String name;
    private final BlockingMode blockingMode;
    private final boolean reportsProgress;
    private final Function<? super ProgressContext, T> work;

    public AsyncAction(String name, BlockingMode blockingMode, Function<TaskContext, T> work) {
        this(name, blockingMode, false, work);
    }

    AsyncAction(String name, BlockingMode blockingMode, boolean reportsProgress,
            Function<? super ProgressContext, T> work) {
        this.name = name;
        this.blockingMode = blockingMode;
        this.reportsProgress = reportsProgress;
        this.work = work;
    }

    public String name() {
        return name;
    }

    public BlockingMode blockingMode() {
        return blockingMode;
    }

    /** Whether Tasks of this Action report Progress; the others show as indeterminate. */
    public boolean reportsProgress() {
        return reportsProgress;
    }

    /** Starts a new Task of this Action in the given Task list. */
    public CompletableFuture<T> start(TaskList taskList) {
        Task task = taskList.add(this);
        CompletableFuture<T> future = new CompletableFuture<>();
        future.whenComplete((value, error) -> {
            if (future.isCancelled()) {
                task.cancel();
            }
        });
        taskList.executor().execute(() -> {
            try {
                ProgressContext context = task.context();
                T value = work.apply(context);
                if (context.isCancelled()) {
                    throw new CancellationException();
                }
                task.succeed();
                future.complete(value);
            } catch (CancellationException e) {
                task.markCancelled();
                future.completeExceptionally(e);
            } catch (RuntimeException e) {
                log.error("Task of {} failed", name, e);
                task.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
