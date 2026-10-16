package com.example.taskmanager.ui;

import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.signals.Signal;

/**
 * A button that starts a Task of its Action and follows it. While the Action has
 * a Running Task, in any tab of the session, it says so and is disabled. A
 * Blocking Task covers this tab with a {@link BlockingTaskDialog}.
 */
public class ActionButton extends Button {

    /** A button that says "Start". */
    public ActionButton(AsyncAction<?> action, TaskList taskList) {
        this(action, taskList, "Start");
    }

    /** A button with its own text, such as "Import". Its accessible name stays "Start" and the Action's name. */
    public ActionButton(AsyncAction<?> action, TaskList taskList, String text) {
        Signal<Boolean> running = taskList.isRunning(action);
        bindText(running.map(r -> r ? action.name() + " running…" : text));
        bindEnabled(Signal.not(running));
        setAriaLabel("Start " + action.name());
        // A click that loses the race with another tab never gets here: by then the
        // button is disabled, and Vaadin drops clicks on disabled components
        addClickListener(e -> BlockingTaskDialog.openIfBlocking(action.startTask(taskList)));
    }
}
