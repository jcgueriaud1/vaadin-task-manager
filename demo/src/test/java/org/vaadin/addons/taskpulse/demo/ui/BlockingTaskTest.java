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
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.server.VaadinSession;
import org.vaadin.addons.taskpulse.PulseTaskList;
import org.vaadin.addons.taskpulse.TestExecutor;
import org.vaadin.addons.taskpulse.PulseTray;

/** A Blocking Task covers the tab that started it with a dialog until it finishes. */
@SpringBootTest
@Import(TestTaskConfig.class)
class BlockingTaskTest extends SpringBrowserlessTest {

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
        executor.runAll();
    }

    @Test
    void startingABlockingActionOpensADialogTheUserCannotDismiss() {
        assertFalse($(Dialog.class).exists());

        test(startButton("Import data")).click();
        runPendingSignalsTasks();

        Dialog dialog = $(Dialog.class).single();
        assertTrue(dialog.isOpened());
        assertTrue(dialog.isModal());
        assertFalse(dialog.isCloseOnEsc());
        assertFalse(dialog.isCloseOnOutsideClick());
        assertEquals("Import data", dialog.getHeaderTitle());
    }

    @Test
    void theDialogFollowsTheProgressAndStatusTextOfItsTask() {
        test(startButton("Import data")).click();
        executor.runNext();
        runPendingSignalsTasks();

        Dialog dialog = $(Dialog.class).single();
        ProgressBar bar = $(ProgressBar.class).from(dialog).single();
        assertFalse(bar.isIndeterminate());
        assertEquals(0.25, bar.getValue());
        assertTrue(dialog.getElement().getTextRecursively().contains("Reading file 1"));

        executor.resume();
        runPendingSignalsTasks();
        assertEquals(0.75, bar.getValue());
        assertTrue(dialog.getElement().getTextRecursively().contains("Reading file 2"));
    }

    @Test
    void theDialogOfAnActionWithoutProgressShowsAnIndeterminateBar() {
        test(startButton("Restore backup")).click();
        executor.runNext();
        runPendingSignalsTasks();

        Dialog dialog = $(Dialog.class).single();
        assertTrue($(ProgressBar.class).from(dialog).single().isIndeterminate());
        assertTrue(dialog.getElement().getTextRecursively().contains("Checking backup"));
    }

    @Test
    void cancelInTheDialogCancelsTheTaskAndClosesTheDialog() {
        test(startButton("Import data")).click();
        executor.runNext();
        runPendingSignalsTasks();

        test($(Button.class).from($(Dialog.class).single()).withText("Cancel").single()).click();
        executor.resume();
        runPendingSignalsTasks();

        assertFalse($(Dialog.class).exists());
        assertTrue(trayText().contains("Cancelled"), trayText());
    }

    @Test
    void theDialogClosesByItselfWhenTheTaskSucceeds() {
        test(startButton("Import data")).click();
        executor.runNext();
        runPendingSignalsTasks();
        assertTrue($(Dialog.class).exists());

        executor.runAll();
        runPendingSignalsTasks();

        assertFalse($(Dialog.class).exists());
        assertTrue(trayText().contains("Succeeded"), trayText());
    }

    @Test
    void theDialogClosesByItselfWhenTheTaskFails() {
        test(startButton("Restore backup")).click();
        executor.runNext();
        runPendingSignalsTasks();
        assertTrue($(Dialog.class).exists());

        executor.runAll();
        runPendingSignalsTasks();

        assertFalse($(Dialog.class).exists());
        assertTrue(trayText().contains("Backup is corrupt"), trayText());
    }

    @Test
    void retryingABlockingTaskBlocksTheTabItWasRetriedFrom() {
        test(startButton("Restore backup")).click();
        executor.runAll();
        runPendingSignalsTasks();
        long failed = context.getBean(PulseTaskList.class).tasks().peek().getFirst().peek().id();
        navigate("tasks/" + failed, TasksView.class);
        assertFalse($(Dialog.class).exists());

        test($(Button.class).withText("Retry").single()).click();
        runPendingSignalsTasks();

        Dialog dialog = $(Dialog.class).single();
        assertEquals("Restore backup", dialog.getHeaderTitle());
    }

    @Test
    void anotherTabOfTheSessionIsNotBlockedAndShowsTheTaskInItsTray() {
        UI starting = UI.getCurrent();
        UI other = openTab();

        test(startButton("Import data")).click();
        executor.runNext();
        runPendingSignalsTasks();

        assertTrue($(Dialog.class).from(starting).exists());
        assertFalse($(Dialog.class).from(other).exists());
        String otherTray = $(PulseTray.class).from(other).single().getElement().getTextRecursively();
        assertTrue(otherTray.contains("Import data"), otherTray);
        assertTrue(otherTray.contains("Running"), otherTray);
    }

    @Test
    void afterAReloadTheTaskIsStillRunningInTheTrayWithoutADialog() {
        test(startButton("Import data")).click();
        executor.runNext();
        runPendingSignalsTasks();

        UI reloaded = openTab();
        runPendingSignalsTasks();

        assertFalse($(Dialog.class).from(reloaded).exists());
        String tray = $(PulseTray.class).from(reloaded).single().getElement().getTextRecursively();
        assertTrue(tray.contains("Import data"), tray);
        assertTrue(tray.contains("Running"), tray);
        assertTrue(tray.contains("Reading file 1"), tray);
    }

    /** Opens a new UI of the current session, as a new tab or a reload would, and keeps the current UI current. */
    private UI openTab() {
        UI current = UI.getCurrent();
        UI tab = new UI();
        tab.getInternals().setSession(VaadinSession.getCurrent());
        tab.add(new MainLayout(context.getBean(PulseTaskList.class)));
        UI.setCurrent(current);
        return tab;
    }

    private String trayText() {
        return $(PulseTray.class).single().getElement().getTextRecursively();
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
