package com.example.taskmanager.ui;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.signals.Signal;

/**
 * The navbar button that says how many Tasks are Running, with a ring for their
 * overall Progress. It opens a popover with the Running and recent Finished Tasks.
 */
public class TaskCenter extends Div {

    static final int RECENT_FINISHED = 5;

    public TaskCenter(TaskList taskList) {
        addClassName("task-center");
        Signal<Integer> runningCount = taskList.runningCount();
        Button button = new Button(runningCount.map(TaskCenter::buttonText));
        button.setAriaLabel("Task center");
        button.addClassName("task-center-button");
        button.bindClassName("running", runningCount.map(count -> count > 0));
        button.setPrefixComponent(buttonIcon(taskList));
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
        Span empty = new Span("No background tasks yet. Start one from the Actions page.");
        empty.addClassName("task-center-empty");
        empty.bindVisible(Signal.computed(() -> taskList.tasks().getValues().findAny().isEmpty()));
        Anchor viewAll = new Anchor("tasks", "View all");
        viewAll.addClassName("task-center-view-all");
        Div header = new Div(new H3("Background tasks"), viewAll);
        header.addClassName("task-center-header");

        Popover popover = new Popover(header, rows, empty);
        popover.addClassName("task-center-popover");
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.setTarget(button);
    }

    /** For example "2 running", or "Tasks" when nothing is Running. */
    static String buttonText(int running) {
        return running > 0 ? running + " running" : "Tasks";
    }

    /** A ring for the overall Progress, a spinning one if no Running Task reports it, a list icon when idle. */
    private static Div buttonIcon(TaskList taskList) {
        Signal<Optional<Double>> overall = taskList.overallProgress();
        Signal<Boolean> running = taskList.runningCount().map(count -> count > 0);
        ProgressRing progress = new ProgressRing(overall.map(p -> p.orElse(0.0)), overall.map(Optional::isPresent));
        progress.bindVisible(running);
        Icon idle = VaadinIcon.TASKS.create();
        idle.bindVisible(Signal.not(running));
        Div icon = new Div(progress, idle);
        icon.addClassName("task-center-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        return icon;
    }

    /** Running Tasks, then the most recent Finished ones, newest first. */
    private static List<Task> shown(TaskList taskList) {
        List<Task> newestFirst = taskList.tasks().getValues().toList().reversed();
        Stream<Task> running = newestFirst.stream()
                .filter(task -> task.running().get());
        Stream<Task> finished = newestFirst.stream()
                .filter(task -> !task.running().get())
                .limit(RECENT_FINISHED);
        return Stream.concat(running, finished).toList();
    }

    private static Div row(Task task) {
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Div body = new Div(name, TaskTray.meta(task));
        body.addClassName("task-row-body");
        Div row = new Div(TaskTray.indicator(task), body, TaskTray.detailLink(task));
        row.addClassName("task-center-row");
        return row;
    }
}
