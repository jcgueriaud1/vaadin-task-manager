package com.example.taskmanager.task;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.Executor;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;

/** All Tasks of one user session, Running and Finished. */
public class TaskList {

    /** The Task list keeps at most this many Tasks, evicting the oldest Finished Task first. */
    public static final int MAX_TASKS = 50;

    private final Executor executor;
    private final Clock clock;
    private final ListSignal<Task> tasks = new ListSignal<>();
    private long nextId = 1;

    public TaskList(Executor executor) {
        this(executor, Clock.systemDefaultZone());
    }

    /** A Task list whose Tasks take their start and finish times from the clock. */
    public TaskList(Executor executor, Clock clock) {
        this.executor = executor;
        this.clock = clock;
    }

    public ListSignal<Task> tasks() {
        return tasks;
    }

    Executor executor() {
        return executor;
    }

    /** The Task with the given id, if it is still in this list. */
    public Optional<Task> find(long id) {
        return tasks.peekValues().filter(task -> task.id() == id).findFirst();
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
     * Starts a new Task of the same Action as a Failed or Cancelled Task. The old
     * Task stays as it was.
     */
    public void retry(Task task) {
        TaskState state = task.state().peek();
        if (state != TaskState.FAILED && state != TaskState.CANCELLED) {
            throw new IllegalStateException("Only a Failed or Cancelled Task can be retried");
        }
        task.action().start(this);
    }

    /** Removes every Finished Task from the Task list. Running Tasks stay. */
    public synchronized void clearFinished() {
        tasks.peek().stream()
                .filter(entry -> entry.peek().state().peek() != TaskState.RUNNING)
                .forEach(tasks::remove);
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
     * one: an Action has at most one Running Task per session. When the list is
     * full, the oldest Finished Task makes room.
     */
    synchronized Task add(AsyncAction<?> action) {
        boolean running = tasks.peekValues()
                .anyMatch(task -> task.action() == action && task.state().peek() == TaskState.RUNNING);
        if (running) {
            throw new IllegalStateException(action.name() + " already has a Running Task");
        }
        if (tasks.peek().size() >= MAX_TASKS) {
            var oldestFinished = tasks.peek().stream()
                    .filter(entry -> entry.peek().state().peek() != TaskState.RUNNING)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(MAX_TASKS + " Tasks are already Running"));
            tasks.remove(oldestFinished);
        }
        Task task = new Task(nextId++, action, clock);
        tasks.insertLast(task);
        return task;
    }
}
