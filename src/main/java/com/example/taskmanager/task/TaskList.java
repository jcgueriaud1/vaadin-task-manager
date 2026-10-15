package com.example.taskmanager.task;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.Executor;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;

/** All Tasks of one user session, Running and Finished. */
public class TaskList {

    private final Executor executor;
    private final ListSignal<Task> tasks = new ListSignal<>();

    public TaskList(Executor executor) {
        this.executor = executor;
    }

    public ListSignal<Task> tasks() {
        return tasks;
    }

    Executor executor() {
        return executor;
    }

    /** Whether the given Action has a Running Task in this list. */
    public Signal<Boolean> isRunning(AsyncAction<?> action) {
        return Signal.computed(() -> tasks.getValues()
                .anyMatch(task -> task.action() == action && task.state().get() == TaskState.RUNNING));
    }

    /**
     * The average Progress of the Running Tasks that report Progress, or empty if
     * there are none.
     */
    public Signal<Optional<Double>> overallProgress() {
        return Signal.computed(() -> {
            OptionalDouble average = tasks.getValues()
                    .filter(task -> task.action().reportsProgress() && task.state().get() == TaskState.RUNNING)
                    .mapToDouble(task -> task.progress().get())
                    .average();
            return average.isPresent() ? Optional.of(average.getAsDouble()) : Optional.empty();
        });
    }

    /**
     * Adds a new Running Task of the Action, refusing if the Action already has
     * one: an Action has at most one Running Task per session.
     */
    synchronized Task add(AsyncAction<?> action) {
        boolean running = tasks.peekValues()
                .anyMatch(task -> task.action() == action && task.state().peek() == TaskState.RUNNING);
        if (running) {
            throw new IllegalStateException(action.name() + " already has a Running Task");
        }
        Task task = new Task(action);
        tasks.insertLast(task);
        return task;
    }
}
