package com.example.taskmanager.ui;

import com.example.taskmanager.DemoActions;
import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;

/** One card per demo Action, each with a start button. */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Actions")
public class ActionsView extends Div {

    public ActionsView(DemoActions demoActions, TaskList taskList) {
        addClassName("actions-view");
        demoActions.actions().forEach(action -> add(card(action, taskList)));
    }

    private static Div card(AsyncAction<?> action, TaskList taskList) {
        Signal<Boolean> running = taskList.isRunning(action);
        Button start = new Button(running.map(r -> r ? action.name() + " running…" : "Start"));
        start.addClickListener(e -> action.start(taskList));
        start.setAriaLabel("Start " + action.name());
        start.bindEnabled(Signal.not(running));
        Div card = new Div(new H2(action.name()), start);
        card.addClassName("action-card");
        return card;
    }
}
