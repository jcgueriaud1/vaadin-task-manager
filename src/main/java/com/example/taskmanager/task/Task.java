package com.example.taskmanager.task;

import java.util.concurrent.CancellationException;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/** One run of an Action. Its fixed fields never change; the rest are signals. */
public final class Task {

    private final AsyncAction<?> action;
    private final ValueSignal<TaskState> state = new ValueSignal<>(TaskState.RUNNING);
    private final ValueSignal<String> statusText = new ValueSignal<>("");
    private final ValueSignal<String> errorMessage = new ValueSignal<>("");
    private volatile boolean cancelled;

    Task(AsyncAction<?> action) {
        this.action = action;
    }

    public AsyncAction<?> action() {
        return action;
    }

    public Signal<TaskState> state() {
        return state.asReadonly();
    }

    public Signal<String> statusText() {
        return statusText.asReadonly();
    }

    /** The message of the exception a Failed Task ended with, empty otherwise. */
    public Signal<String> errorMessage() {
        return errorMessage.asReadonly();
    }

    /**
     * Asks the Task to stop. The work stops the next time it reports, and the
     * Task then becomes Cancelled.
     */
    public void cancel() {
        cancelled = true;
    }

    TaskContext context() {
        return new TaskContext() {
            @Override
            public void setStatusText(String text) {
                if (cancelled) {
                    throw new CancellationException();
                }
                statusText.set(text);
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }

    void succeed() {
        state.set(TaskState.SUCCEEDED);
    }

    void fail(String message) {
        errorMessage.set(message);
        state.set(TaskState.FAILED);
    }

    void markCancelled() {
        state.set(TaskState.CANCELLED);
    }
}
