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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.progressbar.ProgressBar;

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
    void aTaskWithoutProgressShowsAnIndeterminateIndicator() {
        start("Send newsletter");
        runPendingSignalsTasks();

        ProgressBar indicator = rowIndicator("Send newsletter");
        assertTrue(indicator.isIndeterminate());
        assertFalse(overallBarShown());
    }

    @Test
    void rowIndicatorAndOverallBarFollowTheProgressUntilTheResultSummaryShows() {
        assertFalse(overallBarShown());

        start("Generate report");
        runPendingSignalsTasks();
        assertFalse(rowIndicator("Generate report").isIndeterminate());
        assertEquals(0.0, rowIndicator("Generate report").getValue());
        assertTrue(overallBarShown());
        assertEquals(0.0, overallBar().getValue());

        executor.runNext();
        runPendingSignalsTasks();
        assertEquals(0.5, rowIndicator("Generate report").getValue());
        assertEquals(0.5, overallBar().getValue());
        assertFalse(row("Generate report").getElement().getTextRecursively().contains("5 PDF files"));

        executor.resume();
        runPendingSignalsTasks();
        assertFalse($(ProgressBar.class).from(row("Generate report")).exists());
        assertFalse(overallBarShown());
        String rowText = row("Generate report").getElement().getTextRecursively();
        assertTrue(rowText.contains("Succeeded"), rowText);
        assertTrue(rowText.contains("5 PDF files"), rowText);
    }

    private void start(String actionName) {
        test($(Button.class).withAriaLabel("Start " + actionName).single()).click();
    }

    private Div row(String actionName) {
        return $(Div.class).withClassName("task-row")
                .withCondition(row -> row.getElement().getTextRecursively().contains(actionName)).single();
    }

    private ProgressBar rowIndicator(String actionName) {
        return $(ProgressBar.class).from(row(actionName)).single();
    }

    /** Hidden components aren't found by queries. */
    private boolean overallBarShown() {
        return $(ProgressBar.class).withClassName("task-tray-progress").exists();
    }

    private ProgressBar overallBar() {
        return $(ProgressBar.class).withClassName("task-tray-progress").single();
    }
}
