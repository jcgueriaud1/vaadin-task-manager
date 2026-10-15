package com.example.taskmanager.ui;

import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;

/** The app shell: a navbar, the Task tray and the completion toasts, present in every view. */
public class MainLayout extends AppLayout {

    public MainLayout(TaskList taskList) {
        H1 title = new H1("Task Manager");
        title.addClassName("app-title");
        addToNavbar(title, new TaskCenter(taskList));
        getElement().appendChild(new TaskTray(taskList).getElement());
        CompletionToasts.showIn(this, taskList);
    }
}
