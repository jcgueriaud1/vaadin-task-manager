package com.example.taskmanager.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.signals.local.ValueSignal;

/** The Background tasks page: the session's Tasks by Task state, and the details of one. */
@Route(value = "tasks/:taskId?", layout = MainLayout.class)
@PageTitle("Background tasks")
public class TasksView extends Div implements BeforeEnterObserver {

    static final String TASK_ID = "taskId";

    private static final List<TaskState> FILTERS = List.of(TaskState.values());

    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TaskList taskList;
    private final Clock clock;
    private final Tabs tabs = new Tabs();
    private final ValueSignal<Instant> now;
    // The chosen filter and Task are per-tab choices, so they aren't kept in the Task list
    private final ValueSignal<TaskState> filter = new ValueSignal<>(TaskState.RUNNING);
    private final ValueSignal<Optional<Task>> selected = new ValueSignal<>(Optional.empty());

    public TasksView(TaskList taskList, Clock clock) {
        this.taskList = taskList;
        this.clock = clock;
        this.now = new ValueSignal<>(clock.instant());
        addClassName("tasks-view");
        FILTERS.forEach(state -> tabs.add(filterTab(state, taskList)));
        tabs.addSelectedChangeListener(e -> filter.set(FILTERS.get(tabs.getSelectedIndex())));

        Div rows = new Div();
        rows.addClassName("tasks-rows");
        Signal.effect(rows, () -> {
            TaskState shownState = filter.get();
            List<Task> shown = taskList.tasks().getValues()
                    .filter(task -> task.state().get() == shownState)
                    .toList()
                    .reversed();
            rows.removeAll();
            Signal.untracked(() -> {
                shown.forEach(task -> rows.add(row(task)));
                return null;
            });
        });
        Div list = new Div(tabs, rows);
        list.addClassName("tasks-list");

        Div detail = new Div();
        detail.addClassName("task-detail");
        Signal.effect(detail, () -> {
            Optional<Task> task = selected.get();
            detail.removeAll();
            Signal.untracked(() -> {
                task.ifPresent(t -> detail.add(detailContent(t, taskList)));
                return null;
            });
        });
        add(list, detail);
    }

    /** The selected Task comes from the URL, so other parts of the app can link to it. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Task> task = event.getRouteParameters().getLong(TASK_ID).flatMap(taskList::find);
        selected.set(task);
        task.ifPresent(t -> tabs.setSelectedIndex(FILTERS.indexOf(t.state().peek())));
    }

    /** Durations and the time-left estimate move with the clock, so the page polls it while shown. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        ui.setPollInterval(1000);
        Registration poll = ui.addPollListener(e -> now.set(clock.instant()));
        addDetachListener(e -> {
            poll.remove();
            ui.setPollInterval(-1);
            e.unregisterListener();
        });
    }

    private Component[] detailContent(Task task, TaskList taskList) {
        H2 name = new H2(task.action().name());
        name.addClassName("task-detail-name");
        Span state = new Span(task.state().map(TaskTray::label));
        state.addClassName("task-state");
        Span started = new Span("Started " + START_TIME.format(task.startedAt().atZone(clock.getZone())));
        started.addClassName("task-started");
        Span elapsed = new Span(Signal.computed(() -> {
            String prefix = task.state().get() == TaskState.RUNNING ? "Running for " : "Took ";
            return prefix + format(task.elapsed(now.get()));
        }));
        elapsed.addClassName("task-elapsed");
        Signal<Optional<Duration>> timeLeft = Signal.computed(() -> {
            // Read the signals the estimate depends on so it follows them
            task.state().get();
            task.progress().get();
            return task.timeLeft(now.get());
        });
        Span timeLeftText = new Span(timeLeft.map(left -> left.map(d -> "About " + format(d) + " left").orElse("")));
        timeLeftText.addClassName("task-time-left");
        timeLeftText.bindVisible(timeLeft.map(Optional::isPresent));
        Span statusText = new Span(task.statusText());
        statusText.addClassName("task-status-text");
        Span resultSummary = new Span(task.resultSummary());
        resultSummary.addClassName("task-result-summary");
        resultSummary.bindVisible(task.state().map(s -> s == TaskState.SUCCEEDED));
        Span errorMessage = new Span(task.errorMessage());
        errorMessage.addClassName("task-error-message");
        errorMessage.bindVisible(task.state().map(s -> s == TaskState.FAILED));

        Button cancel = new Button("Cancel", e -> task.cancel());
        cancel.bindVisible(task.state().map(s -> s == TaskState.RUNNING));
        Button retry = new Button("Retry", e -> taskList.retry(task));
        retry.bindVisible(task.state().map(s -> s == TaskState.FAILED || s == TaskState.CANCELLED));
        retry.bindEnabled(Signal.not(taskList.isRunning(task.action())));
        Div buttons = new Div(cancel, retry);
        buttons.addClassName("task-detail-buttons");

        return new Component[] { name, state, started, elapsed, timeLeftText, TaskTray.progress(task), statusText,
                resultSummary, errorMessage, buttons };
    }

    /** For example "12 s", "1 min 30 s" or "2 h 5 min". */
    static String format(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            return seconds / 60 + " min " + seconds % 60 + " s";
        }
        return seconds / 3600 + " h " + seconds % 3600 / 60 + " min";
    }

    private static Tab filterTab(TaskState state, TaskList taskList) {
        Signal<Long> count = Signal.computed(() -> taskList.tasks().getValues()
                .filter(task -> task.state().get() == state)
                .count());
        Span label = new Span(count.map(n -> TaskTray.label(state) + " (" + n + ")"));
        return new Tab(label);
    }

    private static Div row(Task task) {
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Span statusText = new Span();
        statusText.addClassName("task-status-text");
        statusText.bindText(task.statusText());
        Div row = new Div(name, statusText);
        row.addClassName("tasks-row");
        row.addClickListener(e -> e.getSource().getUI().ifPresent(ui -> ui.navigate(TasksView.class,
                new RouteParameters(TASK_ID, String.valueOf(task.id())))));
        return row;
    }
}
