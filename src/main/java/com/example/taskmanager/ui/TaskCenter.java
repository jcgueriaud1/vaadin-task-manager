package com.example.taskmanager.ui;

import java.util.List;
import java.util.stream.Stream;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.signals.Signal;

/**
 * The navbar button that counts Running Tasks. It opens a popover with the
 * Running and recent Finished Tasks.
 */
public class TaskCenter extends Div {

    static final int RECENT_FINISHED = 5;

    public TaskCenter(TaskList taskList) {
        addClassName("task-center");
        Button button = new Button(taskList.runningCount().map(String::valueOf));
        button.setAriaLabel("Task center");
        Span unseen = new Span(taskList.unseenCount().map(String::valueOf));
        unseen.addClassName("unseen-badge");
        unseen.getElement().setAttribute("aria-label", "Unseen Tasks");
        unseen.bindVisible(taskList.unseenCount().map(count -> count > 0));
        add(button, unseen);

        Div rows = new Div();
        rows.addClassName("task-center-rows");
        Signal.effect(rows, () -> {
            List<Task> shown = shown(taskList);
            rows.removeAll();
            Signal.untracked(() -> {
                shown.forEach(task -> rows.add(row(task)));
                return null;
            });
        });
        Anchor viewAll = new Anchor("tasks", "View all");

        Popover popover = new Popover(rows, viewAll);
        popover.addClassName("task-center-popover");
        popover.setTarget(button);
    }

    /** Running Tasks, then the most recent Finished ones, newest first. */
    private static List<Task> shown(TaskList taskList) {
        List<Task> newestFirst = taskList.tasks().getValues().toList().reversed();
        Stream<Task> running = newestFirst.stream()
                .filter(task -> task.state().get() == TaskState.RUNNING);
        Stream<Task> finished = newestFirst.stream()
                .filter(task -> task.state().get() != TaskState.RUNNING)
                .limit(RECENT_FINISHED);
        return Stream.concat(running, finished).toList();
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
        Div row = new Div(name, state, statusText, TaskTray.progress(task));
        row.addClassName("task-center-row");
        return row;
    }
}
