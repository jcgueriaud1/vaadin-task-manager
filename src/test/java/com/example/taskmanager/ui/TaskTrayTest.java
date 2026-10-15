package com.example.taskmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.example.taskmanager.task.TestExecutor;
import com.vaadin.browserless.ComponentQuery;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverTester;

@SpringBootTest
@Import(TestTaskConfig.class)
class TaskTrayTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @BeforeEach
    void open() {
        navigate(ActionsView.class);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void headlineSaysWhatIsRunningAndWhatIsFinished() {
        test(startButton("Generate report")).click();
        test(startButton("Send newsletter")).click();
        runPendingSignalsTasks();
        assertEquals("2 tasks running", headline());

        executor.runNext();
        runPendingSignalsTasks();
        assertEquals("1 task running · 1 task finished", headline());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("2 tasks finished", headline());
    }

    @Test
    void collapsingHidesTheRowsButKeepsTheHeadline() {
        test(startButton("Generate report")).click();
        runPendingSignalsTasks();
        assertTrue(rowsShown());

        test($(Button.class).from(tray()).withAriaLabel("Collapse tray").single()).click();
        assertFalse(rowsShown());
        assertEquals("1 task running", headline());

        test($(Button.class).from(tray()).withAriaLabel("Expand tray").single()).click();
        assertTrue(rowsShown());
    }

    @Test
    void dismissRemovesAFinishedTaskFromTheTrayButNotFromTheTaskList() {
        test(startButton("Generate report")).click();
        executor.runAll();
        test(startButton("Send newsletter")).click();
        runPendingSignalsTasks();
        assertFalse(dismissButton("Send newsletter").exists(), "Running Tasks can't be Dismissed");

        test(dismissButton("Generate report").single()).click();
        runPendingSignalsTasks();

        assertFalse(tray().getElement().getTextRecursively().contains("Generate report"));
        assertEquals("1 task running", headline());
        PopoverTester.forTarget($(Button.class).withAriaLabel("Task center").single()).clickTarget();
        assertTrue($(Popover.class).single().getElement().getTextRecursively().contains("Generate report"));
    }

    @Test
    void trayHidesWhenNothingIsRunningAndEveryFinishedTaskIsDismissed() {
        assertFalse(trayShown());

        test(startButton("Generate report")).click();
        runPendingSignalsTasks();
        assertTrue(trayShown());

        executor.runAll();
        runPendingSignalsTasks();
        assertTrue(trayShown(), "an undismissed Finished Task keeps the tray up");

        test(dismissButton("Generate report").single()).click();
        runPendingSignalsTasks();
        assertFalse(trayShown());
    }

    private boolean trayShown() {
        return $(TaskTray.class).exists();
    }

    private ComponentQuery<Button> dismissButton(String actionName) {
        return $(Button.class).from(tray()).withAriaLabel("Dismiss " + actionName);
    }

    private boolean rowsShown() {
        return $(Div.class).from(tray()).withClassName("task-row").exists();
    }

    private String headline() {
        return $(Span.class).from(tray()).withClassName("task-tray-headline").single().getText();
    }

    private TaskTray tray() {
        return $(TaskTray.class).single();
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
