package com.example.taskmanager.task;

import java.util.List;
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

    /** How many Tasks in this list are Running. */
    public Signal<Integer> runningCount() {
        return Signal.computed(() -> (int) tasks.getValues()
                .filter(task -> task.state().get() == TaskState.RUNNING)
                .count());
    }

    /** The Tasks the tray shows: all but the Dismissed ones. */
    public Signal<List<Task>> trayTasks() {
        return Signal.computed(() -> tasks.getValues()
                .filter(task -> !task.dismissed().get())
                .toList());
    }

    /** Removes a Finished Task from the tray. It stays in the Task history. */
    public void dismiss(Task task) {
        if (task.state().peek() == TaskState.RUNNING) {
            throw new IllegalStateException("A Running Task cannot be Dismissed");
        }
        task.dismiss();
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
