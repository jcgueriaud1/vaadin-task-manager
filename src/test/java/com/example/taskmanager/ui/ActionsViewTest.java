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
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;

@SpringBootTest
@Import(TestTaskConfig.class)
class ActionsViewTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @BeforeEach
    void open() {
        navigate(ActionsView.class);
    }

    @AfterEach
    void finishLeftoverWork() {
        // The executor is shared by all tests; don't leak work into the next one
        executor.runAll();
    }

    @Test
    void startingFromTheCardShowsTheTaskInTheTray() {
        test(startButton("Send newsletter")).click();

        executor.runNext();
        runPendingSignalsTasks();
        String tray = trayText();
        assertTrue(tray.contains("Send newsletter"), tray);
        assertTrue(tray.contains("Running"), tray);
        assertTrue(tray.contains("Preparing recipients"), tray);

        executor.resume();
        runPendingSignalsTasks();
        assertTrue(trayText().contains("Sending emails"), trayText());

        executor.runAll();
        runPendingSignalsTasks();
        assertTrue(trayText().contains("Succeeded"), trayText());
    }

    @Test
    void startButtonIsLockedWhileItsActionHasARunningTask() {
        Button newsletter = startButton("Send newsletter");
        Button report = startButton("Generate report");
        assertEquals("Start", newsletter.getText());

        test(newsletter).click();
        runPendingSignalsTasks();
        assertFalse(newsletter.isEnabled());
        assertEquals("Send newsletter running…", newsletter.getText());
        assertTrue(report.isEnabled());

        executor.runAll();
        runPendingSignalsTasks();
        assertTrue(newsletter.isEnabled());
        assertEquals("Start", newsletter.getText());
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }

    private String trayText() {
        return $(TaskTray.class).single().getElement().getTextRecursively();
    }
}
