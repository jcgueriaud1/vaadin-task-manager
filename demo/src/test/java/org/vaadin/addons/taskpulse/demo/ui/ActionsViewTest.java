package org.vaadin.addons.taskpulse.demo.ui;

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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import org.vaadin.addons.taskpulse.PulseAction;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;
import org.vaadin.addons.taskpulse.PulseTaskList;
import org.vaadin.addons.taskpulse.TestExecutor;
import org.vaadin.addons.taskpulse.PulseTray;

@SpringBootTest
@Import(TestTaskConfig.class)
class ActionsViewTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void open() {
        navigate(TestActionsView.class);
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
    void startButtonShowsItsActionRunningAndAnotherClickThenStartsNothing() {
        Button newsletter = startButton("Send newsletter");
        Button report = startButton("Generate report");

        test(newsletter).click();
        runPendingSignalsTasks();
        assertEquals("running", phase(newsletter));
        assertEquals("Send newsletter running", newsletter.getElement().getAttribute("aria-label"));
        assertEquals("idle", phase(report));

        test(newsletter).click();
        runPendingSignalsTasks();
        assertEquals(1, executor.queued(), "one Running Task per Action");

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("succeeded", phase(newsletter));
        assertEquals("Send newsletter done", newsletter.getElement().getAttribute("aria-label"));
    }

    @Test
    void cancellingFromTheTrayEndsTheTaskCancelledAndFreesItsAction() {
        Button newsletter = startButton("Send newsletter");
        test(newsletter).click();
        executor.runNext();
        runPendingSignalsTasks();

        test(cancelButton("Send newsletter")).click();
        executor.resume();
        runPendingSignalsTasks();

        String tray = trayText();
        assertTrue(tray.contains("Send newsletter"), tray);
        assertTrue(tray.contains("Cancelled"), tray);
        assertFalse(tray.contains("Sending emails"), tray);
        assertFalse($(Button.class).withAriaLabel("Cancel Send newsletter").exists());
        assertEquals("cancelled", phase(newsletter));
        assertEquals("Start Send newsletter", newsletter.getElement().getAttribute("aria-label"));
    }

    @Test
    void aFailedTaskShowsItsErrorMessageInTheTray() {
        Button sync = startButton("Sync with server");
        test(sync).click();

        executor.runAll();
        runPendingSignalsTasks();

        String tray = trayText();
        assertTrue(tray.contains("Failed"), tray);
        assertTrue(tray.contains("Server unreachable"), tray);
        assertEquals("failed", phase(sync));
    }

    @Test
    void aClickThatLosesTheRaceWithAnotherTabStartsNothing() {
        Button importData = startButton("Import data");
        // Another tab starts it while this tab's click is on its way to the server: this tab isn't current then
        UI thisTab = UI.getCurrent();
        UI.setCurrent(null);
        context.getBean(PulseTaskList.class).start(new PulseAction("Import data", BlockingMode.BLOCKING), executor, ctx -> {});
        UI.setCurrent(thisTab);
        runPendingSignalsTasks();

        test(importData).click();
        runPendingSignalsTasks();

        assertEquals(1, executor.queued(), "only the other tab's Task");
        assertFalse($(Dialog.class).exists());
        assertEquals("running", phase(importData));
    }

    private Button cancelButton(String actionName) {
        return $(Button.class).withAriaLabel("Cancel " + actionName).single();
    }

    private static String phase(Button startButton) {
        return startButton.getElement().getAttribute("data-phase");
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAttribute("data-resting-name", "Start " + actionName).single();
    }

    private String trayText() {
        return $(PulseTray.class).single().getElement().getTextRecursively();
    }
}
