package com.example.taskmanager.ui;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** The floating tray in the bottom-right that lists the session's Tasks. */
public class TaskTray extends Div {

    public TaskTray(TaskList taskList) {
        addClassName("task-tray");
        bindChildren(taskList.tasks(), entry -> row(entry.peek()));
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
        Div row = new Div(name, state, statusText);
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
