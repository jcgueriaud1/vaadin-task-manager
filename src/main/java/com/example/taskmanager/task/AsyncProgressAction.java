package com.example.taskmanager.task;

import java.util.function.Function;

/**
 * An Action that reports Progress: its work function gets a context that also
 * accepts Progress from 0 to 1.
 *
 * @param <T> the type of value the work produces
 */
public class AsyncProgressAction<T> extends AsyncAction<T> {

    public AsyncProgressAction(String name, BlockingMode blockingMode, Function<ProgressContext, T> work) {
        super(name, blockingMode, true, work);
    }
}
