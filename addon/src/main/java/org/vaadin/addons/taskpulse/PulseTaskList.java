package org.vaadin.addons.taskpulse;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.component.UI;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;
import org.vaadin.addons.taskpulse.PulseAction.Retry;

/**
 * All Tasks of one user session, Running and Finished. Every browser tab of the
 * session sees the same list: get it with {@link #getCurrent()}.
 */
public class PulseTaskList {

    /** The Task list keeps at most this many Tasks, evicting the oldest Finished Task first. */
    public static final int MAX_TASKS = 50;

    /** Runs each Task's work on a virtual thread of its own. */
    private static final Executor VIRTUAL_THREADS = command -> Thread.ofVirtual().name("taskpulse-", 0).start(command);

    private final Clock clock;
    private final Executor executor;
    private final ListSignal<PulseTask> tasks = new ListSignal<>();
    private final List<Tab> tabs = new ArrayList<>();
    private long nextId = 1;

    public PulseTaskList() {
        this(Clock.systemDefaultZone());
    }

    /** A Task list whose Tasks take their start and finish times from the clock. */
    public PulseTaskList(Clock clock) {
        this(clock, VIRTUAL_THREADS);
    }

    /**
     * A Task list whose Tasks take their times from the clock, and whose work
     * runs on the executor unless a start says otherwise. Without one, each Task's
     * work runs on a virtual thread of its own.
     */
    public PulseTaskList(Clock clock, Executor executor) {
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * The Task list of the current session, created on first use. It is kept as
     * an attribute of the {@link VaadinSession}.
     *
     * @throws IllegalStateException if there is no current session
     */
    public static PulseTaskList getCurrent() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            throw new IllegalStateException("There is no current VaadinSession");
        }
        return get(session);
    }

    /** The Task list of the session, created on first use. */
    public static PulseTaskList get(VaadinSession session) {
        session.lock();
        try {
            PulseTaskList taskList = session.getAttribute(PulseTaskList.class);
            if (taskList == null) {
                taskList = new PulseTaskList();
                session.setAttribute(PulseTaskList.class, taskList);
            }
            return taskList;
        } finally {
            session.unlock();
        }
    }

    /**
     * Makes the given Task list the session's, for example one with another
     * clock. Call it before anything uses the session's Task list.
     *
     * @return the given Task list
     * @throws IllegalStateException if the session already has a Task list
     */
    public static PulseTaskList install(VaadinSession session, PulseTaskList taskList) {
        session.lock();
        try {
            if (session.getAttribute(PulseTaskList.class) != null) {
                throw new IllegalStateException("The session already has a Task list");
            }
            session.setAttribute(PulseTaskList.class, taskList);
            return taskList;
        } finally {
            session.unlock();
        }
    }

    public ListSignal<PulseTask> tasks() {
        return tasks;
    }

    /** The Task with the given id, if it is still in this list. */
    public Optional<PulseTask> find(long id) {
        return tasks.peekValues().filter(task -> task.id() == id).findFirst();
    }

    /** Whether the given Action has a Running Task in this list. */
    public Signal<Boolean> isRunning(PulseAction action) {
        return Signal.computed(() -> tasks.getValues()
                .anyMatch(task -> task.action().equals(action) && task.state().get() == PulseTask.State.RUNNING));
    }

    /** How many Tasks in this list are Running. */
    public Signal<Integer> runningCount() {
        return Signal.computed(() -> (int) tasks.getValues()
                .filter(task -> task.state().get() == PulseTask.State.RUNNING)
                .count());
    }

    /** The Tasks the tray shows: all but the Dismissed ones. */
    public Signal<List<PulseTask>> trayTasks() {
        return Signal.computed(() -> tasks.getValues()
                .filter(task -> !task.dismissed().get())
                .toList());
    }

    /** Removes a Finished Task from the tray. It stays in the Task history. */
    public void dismiss(PulseTask task) {
        if (task.state().peek() == PulseTask.State.RUNNING) {
            throw new IllegalStateException("A Running Task cannot be Dismissed");
        }
        task.dismiss();
    }

    /**
     * Starts a new Task of the same Action as a Failed or Cancelled Task, with the
     * same work on the same executor, and returns it. The old Task stays as it was.
     * Retried from a browser tab, a Task of a Blocking Action covers that tab.
     *
     * @throws IllegalStateException if the Task is not Failed or Cancelled, its
     *                               Action is {@link Retry#NEVER}, or the
     *                               Action already has a Running Task
     */
    public PulseTask retry(PulseTask task) {
        return task.retry();
    }

    /** Removes every Finished Task from the Task list. Running Tasks stay. */
    public synchronized void clearFinished() {
        tasks.peek().stream()
                .filter(entry -> entry.peek().state().peek() != PulseTask.State.RUNNING)
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

    /** Registers a browser tab of this session. It starts visible. {@link PulseTab} does it for each tab. */
    synchronized Tab openTab() {
        Tab tab = new Tab();
        tabs.add(tab);
        return tab;
    }

    /** A browser tab of the session, which reports whether it is visible. */
    final class Tab {

        private boolean visible = true;

        private Tab() {
        }

        /** Reports whether the tab is visible, focused or not. */
        void setVisible(boolean visible) {
            synchronized (PulseTaskList.this) {
                this.visible = visible;
                if (visible) {
                    tasks.peekValues().forEach(task -> task.markUnseen(false));
                }
            }
        }

        /** The tab is gone; it no longer counts as visible. */
        void close() {
            synchronized (PulseTaskList.this) {
                tabs.remove(this);
            }
        }
    }

    /** Called when a Task finishes: it is Unseen if the session has tabs and none of them is visible. */
    synchronized void finished(PulseTask task) {
        // With no tab tracked, nobody could have missed it
        if (!tabs.isEmpty() && tabs.stream().noneMatch(tab -> tab.visible)) {
            task.markUnseen(true);
        }
    }

    /**
     * Starts a Task of the Action on this Task list's executor. See
     * {@link #start(PulseAction, Executor, Consumer)}.
     */
    public PulseTask start(PulseAction action, Consumer<? super PulseTaskContext> work) {
        return start(action, executor, work);
    }

    /**
     * Starts a Task of the Action: adds it as Running and hands its work to the
     * executor, which runs it once. Pass an executor when the work must run
     * somewhere other than this Task list's executor: Spring's {@code TaskExecutor},
     * or {@code Runnable::run} to run it on the calling thread before this returns.
     * Retry starts the same work on the same executor.
     * <p>
     * Started from a browser tab, a Task of a Blocking Action covers that tab with
     * a dialog until it finishes or the user cancels it. Started with no current
     * UI, for example from a scheduled job, it blocks nothing.
     * <p>
     * The Task always ends. It ends Succeeded when the work returns, Cancelled
     * when the user cancelled it, even before the work started, and Failed when
     * the work throws or the executor refuses it. An {@link Error} also ends it
     * Failed, and is then rethrown on the executor's thread.
     * <p>
     * The work reports only through its {@link PulseTaskContext}. It must not hold on
     * to a view or its components: a Retry, perhaps from another tab after this
     * one closed, runs the same work again.
     *
     * @throws IllegalStateException if the Action already has a Running Task
     */
    public PulseTask start(PulseAction action, Executor executor, Consumer<? super PulseTaskContext> work) {
        PulseTask task = add(action, action.retry() == Retry.ALLOWED ? () -> start(action, executor, work) : null);
        UI ui = UI.getCurrent();
        if (ui != null && action.blockingMode() == BlockingMode.BLOCKING) {
            new PulseBlockingDialog(task).open();
        }
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
    synchronized PulseTask add(PulseAction action, Supplier<PulseTask> retry) {
        boolean running = tasks.peekValues()
                .anyMatch(task -> task.action().equals(action) && task.state().peek() == PulseTask.State.RUNNING);
        if (running) {
            throw new IllegalStateException(action.name() + " already has a Running Task");
        }
        if (tasks.peek().size() >= MAX_TASKS) {
            var oldestFinished = tasks.peek().stream()
                    .filter(entry -> entry.peek().state().peek() != PulseTask.State.RUNNING)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(MAX_TASKS + " Tasks are already Running"));
            tasks.remove(oldestFinished);
        }
        PulseTask task = new PulseTask(nextId++, action, this, retry, clock);
        tasks.insertLast(task);
        return task;
    }
}
