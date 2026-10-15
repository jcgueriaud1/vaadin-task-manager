package com.example.taskmanager.ui;

import java.util.Optional;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.signals.Signal;

/** The floating tray in the bottom-right that lists the session's Tasks. */
public class TaskTray extends Div {

    public TaskTray(TaskList taskList) {
        addClassName("task-tray");
        add(overallProgress(taskList.overallProgress()));
        Div rows = new Div();
        rows.addClassName("task-rows");
        rows.bindChildren(taskList.tasks(), entry -> row(entry.peek()));
        add(rows);
    }

    /** The average Progress of Running Tasks that report Progress, hidden when there are none. */
    private static ProgressBar overallProgress(Signal<Optional<Double>> overall) {
        ProgressBar bar = new ProgressBar();
        bar.addClassName("task-tray-progress");
        bar.bindValue(overall.map(progress -> progress.orElse(0.0)));
        bar.bindVisible(overall.map(Optional::isPresent));
        return bar;
    }

    private static Div row(Task task) {
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Span state = new Span();
        state.addClassName("task-state");
        state.bindText(task.state().map(TaskTray::label));
        Span statusText = new Span();
        statusText.addClassName("task-status-text");
        statusText.bindText(task.statusText());
        Span resultSummary = new Span();
        resultSummary.addClassName("task-result-summary");
        resultSummary.bindText(task.resultSummary());
        resultSummary.bindVisible(task.state().map(s -> s == TaskState.SUCCEEDED));
        Div row = new Div(name, state, statusText, resultSummary, progress(task));
        row.addClassName("task-row");
        return row;
    }

    /** Determinate if the Task's Action reports Progress, indeterminate otherwise; shown while Running. */
    private static ProgressBar progress(Task task) {
        ProgressBar bar = new ProgressBar();
        bar.addClassName("task-progress");
        if (task.action().reportsProgress()) {
            bar.bindValue(task.progress());
        } else {
            bar.setIndeterminate(true);
        }
        bar.bindVisible(task.state().map(s -> s == TaskState.RUNNING));
        return bar;
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
