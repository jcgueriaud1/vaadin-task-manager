package com.example.taskmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TestExecutor;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;

/** An ActionButton starts its Action and follows it, without any wiring in the view. */
@SpringBootTest
@Import(TestTaskConfig.class)
class ActionButtonTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestTaskConfig.TestActions testActions;

    private Div view;

    @BeforeEach
    void open() {
        view = navigate(TestActionsView.class);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void itSaysStartThenThatTheActionIsRunningAndIsLockedMeanwhile() {
        ActionButton button = add(new ActionButton(action("Send newsletter"), taskList()));
        assertEquals("Start", button.getText());
        assertEquals("Start Send newsletter", button.getAriaLabel().orElseThrow());

        test(button).click();
        runPendingSignalsTasks();
        assertEquals("Send newsletter running…", button.getText());
        assertFalse(button.isEnabled());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("Start", button.getText());
        assertTrue(button.isEnabled());
    }

    @Test
    void aCustomTextReplacesStartButNotTheAccessibleName() {
        ActionButton button = add(new ActionButton(action("Send newsletter"), taskList(), "Send"));
        assertEquals("Send", button.getText());
        assertEquals("Start Send newsletter", button.getAriaLabel().orElseThrow());
    }

    @Test
    void everyButtonOfTheActionIsLockedWhileItRuns() {
        ActionButton first = add(new ActionButton(action("Send newsletter"), taskList()));
        ActionButton second = add(new ActionButton(action("Send newsletter"), taskList(), "Send"));

        test(first).click();
        runPendingSignalsTasks();

        assertFalse(second.isEnabled());
        assertEquals("Send newsletter running…", second.getText());
    }

    @Test
    void aBlockingActionOpensTheBlockingTaskDialog() {
        ActionButton button = add(new ActionButton(action("Import data"), taskList()));

        test(button).click();
        runPendingSignalsTasks();

        assertEquals("Import data", $(Dialog.class).single().getHeaderTitle());
    }

    @Test
    void aClickThatLosesTheRaceWithAnotherTabIsDroppedAsTheButtonIsAlreadyLocked() {
        ActionButton button = add(new ActionButton(action("Import data"), taskList()));
        // Another tab starts it while this tab's click is on its way to the server
        action("Import data").start(taskList());

        assertFalse(button.isEnabled());
        button.click();
        runPendingSignalsTasks();

        assertEquals(1, executor.queued());
        assertFalse($(Dialog.class).exists());
        assertEquals("Import data running…", button.getText());
    }

    private ActionButton add(ActionButton button) {
        view.add(button);
        return button;
    }

    private AsyncAction<?> action(String name) {
        return testActions.actions().stream().filter(a -> a.name().equals(name)).findFirst().orElseThrow();
    }

    private TaskList taskList() {
        return context.getBean(TaskList.class);
    }
}
