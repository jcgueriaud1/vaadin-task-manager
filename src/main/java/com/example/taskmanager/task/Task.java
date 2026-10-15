package com.example.taskmanager.task;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/** One run of an Action. Its fixed fields never change; the rest are signals. */
public final class Task {

    private final AsyncAction<?> action;
    private final ValueSignal<TaskState> state = new ValueSignal<>(TaskState.RUNNING);
    private final ValueSignal<String> statusText = new ValueSignal<>("");
    private final ValueSignal<Boolean> dismissed = new ValueSignal<>(false);

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

    /** Whether the user has removed this Finished Task from the tray. */
    public Signal<Boolean> dismissed() {
        return dismissed.asReadonly();
    }

    TaskContext context() {
        return statusText::set;
    }

    void succeed() {
        state.set(TaskState.SUCCEEDED);
    }

    void dismiss() {
        dismissed.set(true);
    }
}
