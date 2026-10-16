package com.example.taskmanager.ui;

import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** The Actions page with the Actions of {@link TestTaskConfig}, which the tests drive step by step. */
@Route(value = "test-actions", layout = MainLayout.class)
@PageTitle("Actions")
public class TestActionsView extends ActionsView {

    public TestActionsView(TaskList taskList, TestTaskConfig.TestActions testActions) {
        super(taskList, testActions.actions());
    }
}
