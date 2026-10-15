package com.example.taskmanager.task;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * An Action: something the user can start, defined once from a
 * name, a blocking mode and a work function. This one has no Progress; see
 * {@link AsyncProgressAction} for one that has.
 *
 * @param <T> the type of value the work produces
 */
public class AsyncAction<T> {

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
        return CompletableFuture.supplyAsync(() -> {
            T value = work.apply(task.context());
            task.succeed();
            return value;
        }, taskList.executor());
    }
}
