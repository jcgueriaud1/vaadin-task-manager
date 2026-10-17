package com.example.taskmanager.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.taskmanager.task.Outcome;
import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/** The floating tray in the bottom-right that lists the session's Tasks. */
public class TaskTray extends Div {

    public TaskTray(TaskList taskList) {
        addClassName("task-tray");
        getElement().setAttribute("role", "region");
        getElement().setAttribute("aria-label", "Background tasks");
        Span headline = new Span();
        headline.addClassName("task-tray-headline");
        headline.bindText(Signal.computed(() -> headline(taskList.trayTasks().get())));
        // Collapsing is a per-tab choice, so it isn't kept in the Task list
        ValueSignal<Boolean> collapsed = new ValueSignal<>(false);
        Button toggle = new Button(new Icon(VaadinIcon.CHEVRON_DOWN_SMALL));
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        toggle.addClassName("task-tray-toggle");
        toggle.getElement().bindAttribute("aria-label", collapsed.map(c -> c ? "Expand tray" : "Collapse tray"));
        toggle.getElement().bindAttribute("aria-expanded", collapsed.map(c -> String.valueOf(!c)));
        toggle.bindClassName("collapsed", collapsed);
        toggle.addClickListener(e -> collapsed.update(c -> !c));
        Div header = new Div(headline, toggle);
        header.addClassName("task-tray-header");

        Div rows = new Div();
        rows.addClassName("task-tray-rows");
        Signal.effect(rows, () -> {
            List<Task> shown = taskList.trayTasks().get();
            rows.removeAll();
            Signal.untracked(() -> {
                shown.forEach(task -> rows.add(row(task, taskList)));
                return null;
            });
        });
        rows.bindVisible(Signal.not(collapsed));
        add(header, overallProgress(taskList.overallProgress()), rows);
        bindVisible(taskList.trayTasks().map(tasks -> !tasks.isEmpty()));
    }

    /** The average Progress of Running Tasks that report Progress, hidden when there are none. */
    private static ProgressBar overallProgress(Signal<Optional<Double>> overall) {
        ProgressBar bar = new ProgressBar();
        bar.addClassName("task-tray-progress");
        bar.bindValue(overall.map(progress -> progress.orElse(0.0)));
        bar.bindVisible(overall.map(Optional::isPresent));
        return bar;
    }

    /** For example "1 task running · 2 tasks finished", or "" when there's nothing. */
    static String headline(List<Task> tasks) {
        long running = tasks.stream().filter(task -> task.running().get()).count();
        long finished = tasks.size() - running;
        List<String> parts = new ArrayList<>();
        if (running > 0) {
            parts.add(count(running) + " running");
        }
        if (finished > 0) {
            parts.add(count(finished) + " finished");
        }
        return String.join(" · ", parts);
    }

    private static String count(long tasks) {
        return tasks == 1 ? "1 task" : tasks + " tasks";
    }

    private static Div row(Task task, TaskList taskList) {
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Div body = new Div(name, meta(task));
        body.addClassName("task-row-body");

        Button cancel = new Button("Cancel", e -> task.cancel());
        cancel.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        cancel.setAriaLabel("Cancel " + task.action().name());
        cancel.addClassName("task-cancel");
        cancel.bindVisible(task.running());
        RouterLink open = detailLink(task);
        open.bindVisible(Signal.not(task.running()));
        Button dismiss = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> taskList.dismiss(task));
        dismiss.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        dismiss.setAriaLabel("Dismiss " + task.action().name());
        dismiss.addClassName("task-dismiss");
        dismiss.bindVisible(Signal.not(task.running()));
        Div actions = new Div(cancel, open, dismiss);
        actions.addClassName("task-row-actions");

        Div row = new Div(indicator(task), body, actions);
        row.addClassName("task-row");
        return row;
    }

    /** A link to the Task on the Background tasks page. */
    static RouterLink detailLink(Task task) {
        RouterLink open = new RouterLink("Open", TasksView.class,
                new RouteParameters(TasksView.TASK_ID, String.valueOf(task.id())));
        open.addClassName("task-open");
        open.getElement().setAttribute("aria-label", "Open " + task.action().name());
        return open;
    }

    /**
     * What the Task is doing now, for example "50% · Step 2 of 4: aggregating…"
     * or "Running · Preparing recipients" while Running, and "Succeeded · 5 PDF
     * files" or "Failed · Server unreachable" once Finished.
     */
    static Div meta(Task task) {
        Signal<Boolean> showsPercent = task.determinate();
        Span state = new Span(task.state().map(TaskTray::label));
        state.addClassName("task-state");
        state.bindVisible(Signal.not(showsPercent));
        Span percent = new Span(task.progress().map(p -> Math.round(p * 100) + "%"));
        percent.addClassName("task-percent");
        percent.bindVisible(showsPercent);
        Span statusText = new Span(task.statusText());
        statusText.addClassName("task-status-text");
        statusText.bindVisible(task.running());
        Span resultSummary = resultSummary(task);
        Span errorMessage = errorMessage(task);
        Div meta = new Div(state, percent, statusText, resultSummary, errorMessage);
        meta.addClassName("task-meta");
        return meta;
    }

    /** A ring while the Task is Running, then an icon for how it finished. */
    static Div indicator(Task task) {
        ProgressRing ring = new ProgressRing(task.progress(), task.determinate());
        ring.bindVisible(task.running());
        Div indicator = new Div(ring,
                stateIcon(task, TaskState.SUCCEEDED, VaadinIcon.CHECK_CIRCLE),
                stateIcon(task, TaskState.FAILED, VaadinIcon.EXCLAMATION_CIRCLE),
                stateIcon(task, TaskState.CANCELLED, VaadinIcon.CLOSE_CIRCLE));
        indicator.addClassName("task-indicator");
        return indicator;
    }

    private static Component stateIcon(Task task, TaskState state, VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.addClassNames("task-state-icon", state.name().toLowerCase());
        icon.getElement().setAttribute("aria-hidden", "true");
        icon.bindVisible(task.state().map(s -> s == state));
        return icon;
    }

    /** Indeterminate until the Task's work reports Progress; shown while Running. */
    static ProgressBar progress(Task task) {
        ProgressBar bar = new ProgressBar();
        bar.addClassName("task-progress");
        bar.bindValue(task.progress());
        Signal.effect(bar, () -> bar.setIndeterminate(!task.determinate().get()));
        bar.bindVisible(task.running());
        return bar;
    }

    /** The Result summary of a Succeeded Task, shown only once it Succeeded. */
    static Span resultSummary(Task task) {
        Span span = new Span(task.outcome().map(
                o -> o.orElse(null) instanceof Outcome.Succeeded s ? s.resultSummary() : ""));
        span.addClassName("task-result-summary");
        span.bindVisible(task.outcome().map(o -> o.orElse(null) instanceof Outcome.Succeeded));
        return span;
    }

    /** The error of a Failed Task, shown only once it Failed. */
    static Span errorMessage(Task task) {
        Span span = new Span(task.outcome().map(
                o -> o.orElse(null) instanceof Outcome.Failed f ? f.errorMessage() : ""));
        span.addClassName("task-error-message");
        span.bindVisible(task.outcome().map(o -> o.orElse(null) instanceof Outcome.Failed));
        return span;
    }

    static String label(TaskState state) {
        return switch (state) {
            case RUNNING -> "Running";
            case SUCCEEDED -> "Succeeded";
            case FAILED -> "Failed";
            case CANCELLED -> "Cancelled";
        };
    }
}
