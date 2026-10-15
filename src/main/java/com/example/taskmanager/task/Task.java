package com.example.taskmanager.task;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/** One run of an Action. Its fixed fields never change; the rest are signals. */
public final class Task {

    private final AsyncAction<?> action;
    private final ValueSignal<TaskState> state = new ValueSignal<>(TaskState.RUNNING);
    private final ValueSignal<String> statusText = new ValueSignal<>("");
    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<String> resultSummary = new ValueSignal<>("");

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

    /** How much of the Task is done, from 0 to 1. Only meaningful if its Action reports Progress. */
    public Signal<Double> progress() {
        return progress.asReadonly();
    }

    /** What a Succeeded Task produced, or empty if its Action didn't say. */
    public Signal<String> resultSummary() {
        return resultSummary.asReadonly();
    }

    ProgressContext context() {
        return new ProgressContext() {
            @Override
            public void setStatusText(String text) {
                statusText.set(text);
            }

            @Override
            public void setResultSummary(String text) {
                resultSummary.set(text);
            }

            @Override
            public void setProgress(double value) {
                progress.set(Math.clamp(value, 0.0, 1.0));
            }
        };
    }

    void succeed() {
        state.set(TaskState.SUCCEEDED);
    }
}
