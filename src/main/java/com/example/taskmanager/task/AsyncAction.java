package com.example.taskmanager.task;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * An Action without Progress: something the user can start, defined once from a
 * name, a blocking mode and a work function.
 *
 * @param <T> the type of value the work produces
 */
public class AsyncAction<T> {

    private final String name;
    private final BlockingMode blockingMode;
    private final Function<TaskContext, T> work;

    public AsyncAction(String name, BlockingMode blockingMode, Function<TaskContext, T> work) {
        this.name = name;
        this.blockingMode = blockingMode;
        this.work = work;
    }

    public String name() {
        return name;
    }

    public BlockingMode blockingMode() {
        return blockingMode;
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
