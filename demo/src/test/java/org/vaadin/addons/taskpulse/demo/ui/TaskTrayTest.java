package org.vaadin.addons.taskpulse.demo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vaadin.browserless.ComponentQuery;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverTester;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.vaadin.addons.taskpulse.TestExecutor;
import org.vaadin.addons.taskpulse.PulseTray;

@SpringBootTest
@Import(TestTaskConfig.class)
class TaskTrayTest extends SpringBrowserlessTest {

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

    @Test
    void aTaskWithoutProgressShowsAnIndeterminateIndicator() {
        test(startButton("Send newsletter")).click();
        runPendingSignalsTasks();

        Span indicator = rowIndicator("Send newsletter");
        assertTrue(indicator.hasClassName("indeterminate"));
        assertFalse(overallBarShown());
    }

    @Test
    void rowIndicatorAndOverallBarFollowTheProgressUntilTheResultSummaryShows() {
        assertFalse(overallBarShown());

        test(startButton("Export data")).click();
        runPendingSignalsTasks();
        assertTrue(rowIndicator("Export data").hasClassName("indeterminate"), "its work hasn't reported Progress yet");
        assertFalse(overallBarShown());

        executor.runNext();
        runPendingSignalsTasks();
        assertFalse(rowIndicator("Export data").hasClassName("indeterminate"));
        assertTrue(overallBarShown());
        assertEquals("50", rowIndicator("Export data").getElement().getAttribute("aria-valuenow"));
        assertEquals(0.5, overallBar().getValue());
        assertFalse(row("Export data").getElement().getTextRecursively().contains("3 CSV files"));

        executor.resume();
        runPendingSignalsTasks();
        assertFalse($(Span.class).from(row("Export data")).withClassName("pulse-progress-ring").exists());
        assertFalse(overallBarShown());
        String rowText = row("Export data").getElement().getTextRecursively();
        assertTrue(rowText.contains("Succeeded"), rowText);
        assertTrue(rowText.contains("3 CSV files"), rowText);
    }

    private boolean trayShown() {
        return $(PulseTray.class).exists();
    }

    private ComponentQuery<Button> dismissButton(String actionName) {
        return $(Button.class).from(tray()).withAriaLabel("Dismiss " + actionName);
    }

    private boolean rowsShown() {
        return $(Div.class).from(tray()).withClassName("pulse-row").exists();
    }

    private String headline() {
        return $(Span.class).from(tray()).withClassName("pulse-tray-headline").single().getText();
    }

    private PulseTray tray() {
        return $(PulseTray.class).single();
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }

    private Div row(String actionName) {
        return $(Div.class).withClassName("pulse-row")
                .withCondition(row -> row.getElement().getTextRecursively().contains(actionName)).single();
    }

    /** The ring in the Task's row, which follows its Progress. */
    private Span rowIndicator(String actionName) {
        return $(Span.class).from(row(actionName)).withClassName("pulse-progress-ring").single();
    }

    /** Hidden components aren't found by queries. */
    private boolean overallBarShown() {
        return $(ProgressBar.class).withClassName("pulse-tray-progress").exists();
    }

    private ProgressBar overallBar() {
        return $(ProgressBar.class).withClassName("pulse-tray-progress").single();
    }
}
