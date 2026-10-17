package com.example.taskmanager.task;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;

/** All Tasks of one user session, Running and Finished. */
public class TaskList {

    /** The Task list keeps at most this many Tasks, evicting the oldest Finished Task first. */
    public static final int MAX_TASKS = 50;

    private final Clock clock;
    private final ListSignal<Task> tasks = new ListSignal<>();
    private final List<Tab> tabs = new ArrayList<>();
    private long nextId = 1;

    public TaskList() {
        this(Clock.systemDefaultZone());
    }

    /** A Task list whose Tasks take their start and finish times from the clock. */
    public TaskList(Clock clock) {
        this.clock = clock;
    }

    public ListSignal<Task> tasks() {
        return tasks;
    }

    /** The Task with the given id, if it is still in this list. */
    public Optional<Task> find(long id) {
        return tasks.peekValues().filter(task -> task.id() == id).findFirst();
    }

    /** Whether the given Action has a Running Task in this list. */
    public Signal<Boolean> isRunning(Action action) {
        return Signal.computed(() -> tasks.getValues()
                .anyMatch(task -> task.action().equals(action) && task.state().get() == TaskState.RUNNING));
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
     * Starts a new Task of the same Action as a Failed or Cancelled Task, with the
     * same work on the same executor, and returns it. The old Task stays as it was.
     *
     * @throws IllegalStateException if the Task is not Failed or Cancelled, was
     *                               started with {@link Retry#NEVER}, or its
     *                               Action already has a Running Task
     */
    public Task retry(Task task) {
        return task.retry();
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
                    .filter(task -> task.determinate().get())
                    .mapToDouble(task -> task.progress().get())
                    .average();
            return average.isPresent() ? Optional.of(average.getAsDouble()) : Optional.empty();
        });
    }

    /** How many Tasks in this list are Unseen. */
    public Signal<Integer> unseenCount() {
        return Signal.computed(() -> (int) tasks.getValues()
                .filter(task -> task.unseen().get())
                .count());
    }

    /** Registers a browser tab of this session. It starts visible. */
    public synchronized Tab openTab() {
        Tab tab = new Tab();
        tabs.add(tab);
        return tab;
    }

    /** A browser tab of the session, which reports whether it is visible. */
    public final class Tab {

        private boolean visible = true;

        private Tab() {
        }

        /** Reports whether the tab is visible, focused or not. */
        public void setVisible(boolean visible) {
            synchronized (TaskList.this) {
                this.visible = visible;
                if (visible) {
                    tasks.peekValues().forEach(task -> task.markUnseen(false));
                }
            }
        }

        /** The tab is gone; it no longer counts as visible. */
        public void close() {
            synchronized (TaskList.this) {
                tabs.remove(this);
            }
        }
    }

    /** Called when a Task finishes: it is Unseen if no tab of the session is visible. */
    synchronized void finished(Task task) {
        if (tabs.stream().noneMatch(tab -> tab.visible)) {
            task.markUnseen(true);
        }
    }

    /**
     * Starts a Task of the Action that can be Retried. See
     * {@link #start(Action, Executor, Consumer, Retry)}.
     */
    public Task start(Action action, Executor executor, Consumer<? super TaskContext> work) {
        return start(action, executor, work, Retry.ALLOWED);
    }

    /**
     * Starts a Task of the Action: adds it as Running and hands its work to the
     * executor, which runs it once. Pass the executor that fits where the work
     * should run: a virtual-thread executor, Spring's {@code TaskExecutor}, or
     * {@code Runnable::run} to run it on the calling thread before this returns.
     * <p>
     * The Task always ends. It ends Succeeded when the work returns, Cancelled
     * when the user cancelled it, even before the work started, and Failed when
     * the work throws or the executor refuses it. An {@link Error} also ends it
     * Failed, and is then rethrown on the executor's thread.
     * <p>
     * The work reports only through its {@link TaskContext}. It must not hold on
     * to a view or its components: a Retry, perhaps from another tab after this
     * one closed, runs the same work again.
     *
     * @param retry {@link Retry#NEVER} for work that must not run twice
     * @throws IllegalStateException if the Action already has a Running Task
     */
    public Task start(Action action, Executor executor, Consumer<? super TaskContext> work, Retry retry) {
        Task task = add(action, retry == Retry.ALLOWED ? () -> start(action, executor, work, retry) : null);
        try {
            executor.execute(() -> task.run(work));
        } catch (RejectedExecutionException e) {
            task.couldNotStart(e);
        }
        return task;
    }

    /**
     * Adds a new Running Task of the Action. An Action has at most one Running
     * Task per session. When the list is full, the oldest Finished Task makes room.
     */
    synchronized Task add(Action action, Supplier<Task> retry) {
        boolean running = tasks.peekValues()
                .anyMatch(task -> task.action().equals(action) && task.state().peek() == TaskState.RUNNING);
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
        Task task = new Task(nextId++, action, this, retry, clock);
        tasks.insertLast(task);
        return task;
    }
}
