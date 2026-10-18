package org.vaadin.addons.taskpulse.demo.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.vaadin.addons.taskpulse.PulseTask;
import org.vaadin.addons.taskpulse.PulseTaskList;
import org.vaadin.addons.taskpulse.PulseTaskParts;

/** The Background tasks page: the session's Tasks by Task state, and the details of one. */
@Route(value = "tasks/:taskId?", layout = MainLayout.class)
@PageTitle("Background tasks")
public class TasksView extends Div implements BeforeEnterObserver {

    static final String TASK_ID = "taskId";

    private static final List<PulseTask.State> FILTERS = List.of(PulseTask.State.values());

    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PulseTaskList taskList;
    private final Clock clock;
    private final Tabs tabs = new Tabs();
    private final ValueSignal<Instant> now;
    // The chosen filter and Task are per-tab choices, so they aren't kept in the Task list
    private final ValueSignal<PulseTask.State> filter = new ValueSignal<>(PulseTask.State.RUNNING);
    private final ValueSignal<Optional<PulseTask>> selected = new ValueSignal<>(Optional.empty());

    public TasksView(PulseTaskList taskList, Clock clock) {
        this.taskList = taskList;
        this.clock = clock;
        this.now = new ValueSignal<>(clock.instant());
        addClassName("tasks-view");
        FILTERS.forEach(state -> tabs.add(filterTab(state, taskList)));
        tabs.addSelectedChangeListener(e -> filter.set(FILTERS.get(tabs.getSelectedIndex())));

        Div rows = new Div();
        rows.addClassName("tasks-rows");
        Signal.effect(rows, () -> {
            PulseTask.State shownState = filter.get();
            List<PulseTask> shown = taskList.tasks().getValues()
                    .filter(task -> task.state().get() == shownState)
                    .toList()
                    .reversed();
            rows.removeAll();
            Signal.untracked(() -> {
                shown.forEach(task -> rows.add(row(task, selected)));
                return null;
            });
        });
        Span empty = new Span(filter.map(state -> "No " + PulseTaskParts.label(state).toLowerCase() + " tasks."));
        empty.addClassName("tasks-empty");
        empty.bindVisible(Signal.computed(() -> {
            PulseTask.State shownState = filter.get();
            return taskList.tasks().getValues().noneMatch(task -> task.state().get() == shownState);
        }));
        Button clearFinished = new Button("Clear finished", e -> taskList.clearFinished());
        clearFinished.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        Div list = new Div(tabs, rows, empty, clearFinished);
        list.addClassName("tasks-list");

        Div detail = new Div();
        detail.addClassName("task-detail");
        Signal.effect(detail, () -> {
            // A selected Task may since have been cleared or evicted
            Optional<PulseTask> task = selected.get()
                    .filter(t -> taskList.tasks().getValues().anyMatch(t::equals));
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
        Optional<PulseTask> task = event.getRouteParameters().getLong(TASK_ID).flatMap(taskList::find);
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

    private Component[] detailContent(PulseTask task, PulseTaskList taskList) {
        H2 name = new H2(task.action().name());
        name.addClassName("task-detail-name");
        Span state = new Span(task.state().map(PulseTaskParts::label));
        state.addClassName("task-state");
        state.getElement().bindAttribute("data-state", task.state().map(s -> s.name().toLowerCase()));
        Span started = new Span("Started " + START_TIME.format(task.startedAt().atZone(clock.getZone())));
        started.addClassName("task-started");
        Signal<Duration> took = task.elapsed(now);
        Span elapsed = new Span(Signal.computed(
                () -> (task.running().get() ? "Running for " : "Took ") + format(took.get())));
        elapsed.addClassName("task-elapsed");
        Signal<Optional<Duration>> timeLeft = task.timeLeft(now);
        Span timeLeftText = new Span(timeLeft.map(left -> left.map(d -> "About " + format(d) + " left").orElse("")));
        timeLeftText.addClassName("task-time-left");
        timeLeftText.bindVisible(timeLeft.map(Optional::isPresent));
        Span statusText = new Span(task.statusText());
        statusText.addClassName("task-status-text");
        Span resultSummary = PulseTaskParts.resultSummary(task);
        Span errorMessage = PulseTaskParts.errorMessage(task);

        Button cancel = new Button("Cancel", e -> task.cancel());
        cancel.addThemeVariants(ButtonVariant.ERROR);
        cancel.bindVisible(task.running());
        Button retry = new Button("Retry", e -> taskList.retry(task));
        retry.addThemeVariants(ButtonVariant.PRIMARY);
        retry.bindVisible(task.retryable());
        retry.bindEnabled(Signal.not(taskList.isRunning(task.action())));
        Div buttons = new Div(cancel, retry);
        buttons.addClassName("task-detail-buttons");

        Div meta = new Div(state, started, elapsed, timeLeftText);
        meta.addClassName("task-detail-meta");
        Div title = new Div(name, meta);
        title.addClassName("task-detail-title");
        Div header = new Div(title, buttons);
        header.addClassName("task-detail-header");
        return new Component[] { header, PulseTaskParts.progressBar(task), statusText, resultSummary, errorMessage };
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

    private static Tab filterTab(PulseTask.State state, PulseTaskList taskList) {
        Signal<Long> count = Signal.computed(() -> taskList.tasks().getValues()
                .filter(task -> task.state().get() == state)
                .count());
        Span label = new Span(PulseTaskParts.label(state));
        label.addClassName("tab-label");
        Span badge = new Span(count.map(String::valueOf));
        badge.addClassName("tab-count");
        return new Tab(label, badge);
    }

    private Div row(PulseTask task, Signal<Optional<PulseTask>> selected) {
        Span name = new Span(task.action().name());
        name.addClassName("pulse-name");
        Span startedAt = new Span(SHORT_TIME.format(task.startedAt().atZone(clock.getZone())));
        startedAt.addClassName("task-started-at");
        Div body = new Div(name, PulseTaskParts.meta(task));
        body.addClassName("pulse-row-body");
        Div row = new Div(PulseTaskParts.indicator(task), body, startedAt);
        row.addClassName("tasks-row");
        row.bindClassName("selected", selected.map(s -> s.equals(Optional.of(task))));
        row.addClickListener(e -> e.getSource().getUI().ifPresent(ui -> ui.navigate(TasksView.class,
                new RouteParameters(TASK_ID, String.valueOf(task.id())))));
        return row;
    }
}
