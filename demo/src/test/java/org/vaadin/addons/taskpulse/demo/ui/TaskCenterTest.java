package org.vaadin.addons.taskpulse.demo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverTester;
import org.vaadin.addons.taskpulse.TestExecutor;

@SpringBootTest
@Import(TestTaskConfig.class)
class TaskCenterTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @BeforeEach
    void open() {
        navigate(TestActionsView.class);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void buttonShowsTheRunningCount() {
        assertEquals("Tasks", taskCenterButton().getText());

        test(startButton("Send newsletter")).click();
        test(startButton("Generate report")).click();
        runPendingSignalsTasks();
        assertEquals("2 running", taskCenterButton().getText());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("Tasks", taskCenterButton().getText());
    }

    @Test
    void popoverListsRunningAndFinishedTasksWithAViewAllButton() {
        test(startButton("Generate report")).click();
        executor.runAll();
        test(startButton("Send newsletter")).click();
        executor.runNext();

        Popover popover = openPopover();
        String text = popover.getElement().getTextRecursively();
        assertTrue(text.contains("Send newsletter"), text);
        assertTrue(text.contains("Preparing recipients"), text);
        assertTrue(text.contains("Running"), text);
        assertTrue(text.contains("Generate report"), text);
        assertTrue(text.contains("Succeeded"), text);
        assertTrue($(Button.class).from(popover).withText("View all").exists());
    }

    @Test
    void popoverKeepsOnlyTheFiveMostRecentFinishedTasks() {
        for (int i = 0; i < 6; i++) {
            test(startButton("Generate report")).click();
            executor.runAll();
            runPendingSignalsTasks();
        }
        test(startButton("Send newsletter")).click();

        Popover popover = openPopover();
        var rows = $(Div.class).from(popover).withClassName("pulse-center-row").all();
        assertEquals(6, rows.size(), "1 Running + 5 Finished");
        assertTrue(rows.getFirst().getElement().getTextRecursively().contains("Send newsletter"),
                "most recent first");
    }

    @Test
    void viewAllLandsOnTheBackgroundTasksPage() {
        test($(Button.class).from(openPopover()).withText("View all").single()).click();

        assertTrue(getCurrentView() instanceof TasksView);
    }

    private Popover openPopover() {
        PopoverTester tester = PopoverTester.forTarget(taskCenterButton());
        tester.clickTarget();
        runPendingSignalsTasks();
        assertTrue(tester.isOpen());
        return tester.getComponent();
    }

    private Button taskCenterButton() {
        return $(Button.class).withAriaLabel("Task center").single();
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
