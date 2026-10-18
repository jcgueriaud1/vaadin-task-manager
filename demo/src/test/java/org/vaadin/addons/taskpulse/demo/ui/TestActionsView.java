package org.vaadin.addons.taskpulse.demo.ui;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.taskpulse.PulseTaskList;

/** The Actions page with the Actions of {@link TestTaskConfig}, which the tests drive step by step. */
@Route(value = "test-actions", layout = MainLayout.class)
@PageTitle("Actions")
public class TestActionsView extends ActionsView {

    public TestActionsView(PulseTaskList taskList, TestTaskConfig.TestActions testActions) {
        super(taskList, testActions.demos());
    }
}
