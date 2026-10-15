package com.example.taskmanager.ui;

import java.util.ArrayList;
import java.util.List;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/** The floating tray in the bottom-right that lists the session's Tasks. */
public class TaskTray extends Div {

    public TaskTray(TaskList taskList) {
        addClassName("task-tray");
        Span headline = new Span();
        headline.addClassName("task-tray-headline");
        headline.bindText(Signal.computed(() -> headline(taskList.trayTasks().get())));
        // Collapsing is a per-tab choice, so it isn't kept in the Task list
        ValueSignal<Boolean> collapsed = new ValueSignal<>(false);
        Button toggle = new Button(collapsed.map(c -> c ? "▴" : "▾"));
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        toggle.getElement().bindAttribute("aria-label", collapsed.map(c -> c ? "Expand tray" : "Collapse tray"));
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
        add(header, rows);
        bindVisible(taskList.trayTasks().map(tasks -> !tasks.isEmpty()));
    }

    /** For example "1 task running · 2 tasks finished", or "" when there's nothing. */
    static String headline(List<Task> tasks) {
        long running = tasks.stream().filter(task -> task.state().get() == TaskState.RUNNING).count();
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
        Span state = new Span();
        state.addClassName("task-state");
        state.bindText(task.state().map(TaskTray::label));
        Span statusText = new Span();
        statusText.addClassName("task-status-text");
        statusText.bindText(task.statusText());
        Button dismiss = new Button("✕", e -> taskList.dismiss(task));
        dismiss.addThemeVariants(ButtonVariant.TERTIARY);
        dismiss.setAriaLabel("Dismiss " + task.action().name());
        dismiss.addClassName("task-dismiss");
        dismiss.bindVisible(task.state().map(s -> s != TaskState.RUNNING));
        Div row = new Div(name, state, dismiss, statusText);
        row.addClassName("task-row");
        return row;
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
