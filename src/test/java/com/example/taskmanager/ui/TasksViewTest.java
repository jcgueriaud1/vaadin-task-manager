package com.example.taskmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TestClock;
import com.example.taskmanager.task.TestExecutor;
import com.vaadin.browserless.ComponentQuery;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.PollEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.server.VaadinSession;

@SpringBootTest
@Import(TestTaskConfig.class)
class TasksViewTest extends SpringBrowserlessTest {

    private static final Instant NINE_AM = Instant.parse("2026-10-15T09:00:00Z");

    @Autowired
    private TestExecutor executor;

    @Autowired
    private TestClock clock;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void startFromTheActionsPage() {
        clock.set(NINE_AM);
        navigate(TestActionsView.class);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void filterTabsListTheirTasksWithLiveCounts() {
        start("Generate report");
        executor.runAll();
        start("Sync with server");
        executor.runAll();
        start("Send newsletter");
        executor.runNext();

        navigate(TasksView.class);
        runPendingSignalsTasks();

        assertEquals(List.of("Running (1)", "Succeeded (1)", "Failed (1)", "Cancelled (0)"), tabLabels());
        assertEquals(List.of("Send newsletter"), listedTaskNames());

        selectTab("Failed");
        assertEquals(List.of("Sync with server"), listedTaskNames());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals(List.of("Running (0)", "Succeeded (2)", "Failed (1)", "Cancelled (0)"), tabLabels());
    }

    @Test
    void detailPaneShowsARunningTaskWithProgressAndUpdatesLive() {
        start("Export data");
        clock.advance(Duration.ofSeconds(10));
        executor.runNext(); // Progress 0.5
        navigate(TasksView.class);
        select("Export data");

        assertEquals("Export data", $(H2.class).from(detailPane()).single().getText());
        assertEquals("Running", detail("task-state"));
        assertEquals("Started 09:00:00", detail("task-started"));
        assertEquals("Running for 10 s", detail("task-elapsed"));
        assertEquals("About 10 s left", detail("task-time-left"));
        assertEquals(0.5, $(ProgressBar.class).from(detailPane()).single().getValue());
        assertTrue(detailButton("Cancel").exists());
        assertFalse(detailButton("Retry").exists());

        clock.advance(Duration.ofSeconds(80));
        tick();
        assertEquals("Running for 1 min 30 s", detail("task-elapsed"));
        assertEquals("About 1 min 30 s left", detail("task-time-left"));

        executor.resume();
        runPendingSignalsTasks();
        clock.advance(Duration.ofSeconds(5));
        tick();
        assertEquals("Succeeded", detail("task-state"));
        assertEquals("Took 1 min 30 s", detail("task-elapsed"));
        assertEquals("3 CSV files", detail("task-result-summary"));
        assertFalse(detailText("task-time-left").exists());
        assertFalse($(ProgressBar.class).from(detailPane()).exists());
        assertFalse(detailButton("Cancel").exists());
    }

    @Test
    void detailPaneShowsTheStatusTextOfATaskWithoutProgressAndNoTimeLeft() {
        start("Send newsletter");
        executor.runNext();
        navigate(TasksView.class);
        select("Send newsletter");

        assertEquals("Preparing recipients", detail("task-status-text"));
        assertFalse(detailText("task-time-left").exists());

        executor.resume();
        runPendingSignalsTasks();
        assertEquals("Sending emails", detail("task-status-text"));
    }

    @Test
    void detailPaneShowsTheErrorOfAFailedTaskWithRetry() {
        start("Sync with server");
        executor.runAll();
        navigate(TasksView.class);
        selectTab("Failed");
        select("Sync with server");

        assertEquals("Failed", detail("task-state"));
        assertEquals("Server unreachable", detail("task-error-message"));
        assertTrue(detailButton("Retry").exists());
        assertFalse(detailButton("Cancel").exists());
    }

    @Test
    void cancelInTheDetailPaneCancelsTheTask() {
        start("Send newsletter");
        executor.runNext();
        navigate(TasksView.class);
        select("Send newsletter");

        test(detailButton("Cancel").single()).click();
        executor.resume();
        runPendingSignalsTasks();

        assertEquals("Cancelled", detail("task-state"));
    }

    @Test
    void selectingATaskPutsItInTheUrl() {
        start("Generate report");
        executor.runAll();
        navigate(TasksView.class);
        selectTab("Succeeded");

        select("Generate report");

        Task task = taskList().tasks().peekValues().findFirst().orElseThrow();
        assertEquals("tasks/" + task.id(), UI.getCurrent().getActiveViewLocation().getPath());
    }

    @Test
    void openingTheUrlOfATaskSelectsItUnderItsFilter() {
        start("Generate report");
        executor.runAll();
        start("Sync with server");
        executor.runAll();
        Task failed = taskList().tasks().peekValues().toList().get(1);

        navigate("tasks/" + failed.id(), TasksView.class);
        runPendingSignalsTasks();

        assertEquals("Sync with server", $(H2.class).from(detailPane()).single().getText());
        assertEquals(List.of("Sync with server"), listedTaskNames());
    }

    @Test
    void retryStartsANewTaskAndIsDisabledWhileItRuns() {
        start("Sync with server");
        executor.runAll();
        navigate(TasksView.class);
        selectTab("Failed");
        select("Sync with server");

        test(detailButton("Retry").single()).click();
        runPendingSignalsTasks();

        assertEquals(List.of("Running (1)", "Succeeded (0)", "Failed (1)", "Cancelled (0)"), tabLabels());
        assertEquals("Failed", detail("task-state"), "the old Task stays as it was");
        assertFalse(detailButton("Retry").single().isEnabled());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals(List.of("Running (0)", "Succeeded (0)", "Failed (2)", "Cancelled (0)"), tabLabels());
        assertTrue(detailButton("Retry").single().isEnabled());
    }

    @Test
    void clearFinishedRemovesFinishedTasksInEveryTabAndKeepsRunningOnes() {
        start("Generate report");
        executor.runAll();
        start("Sync with server");
        executor.runAll();
        start("Send newsletter");
        executor.runNext();
        navigate(TasksView.class);
        selectTab("Failed");
        select("Sync with server");
        TasksView secondTab = openSecondTab();

        test($(Button.class).withText("Clear finished").single()).click();
        runPendingSignalsTasks();

        List<String> expected = List.of("Running (1)", "Succeeded (0)", "Failed (0)", "Cancelled (0)");
        assertEquals(expected, tabLabels());
        assertEquals(expected, $(Tab.class).from(secondTab).all().stream().map(this::tabLabel).toList());
        assertFalse($(H2.class).from(detailPane()).exists(), "the cleared Task is no longer shown");
    }

    private TasksView openSecondTab() {
        UI first = UI.getCurrent();
        UI second = new UI();
        second.getInternals().setSession(VaadinSession.getCurrent());
        TasksView view = new TasksView(taskList(), clock);
        second.add(view);
        UI.setCurrent(first);
        return view;
    }

    private TaskList taskList() {
        return context.getBean(TaskList.class);
    }

    private void start(String actionName) {
        test($(Button.class).withAriaLabel("Start " + actionName).single()).click();
    }

    private List<String> tabLabels() {
        return $(Tab.class).all().stream().map(this::tabLabel).toList();
    }

    /** For example "Running (1)": the label, then the count from its badge. */
    private String tabLabel(Tab tab) {
        String label = $(Span.class).from(tab).withClassName("tab-label").single().getText();
        String count = $(Span.class).from(tab).withClassName("tab-count").single().getText();
        return label + " (" + count + ")";
    }

    private void selectTab(String state) {
        List<String> labels = tabLabels();
        int index = labels.indexOf(labels.stream().filter(label -> label.startsWith(state)).findFirst().orElseThrow());
        test($(Tabs.class).single()).select(index);
        runPendingSignalsTasks();
    }

    private void select(String actionName) {
        runPendingSignalsTasks();
        Div row = $(Div.class).withClassName("tasks-row").all().stream()
                .filter(r -> r.getElement().getTextRecursively().startsWith(actionName))
                .findFirst().orElseThrow();
        test(row).click();
        runPendingSignalsTasks();
    }

    /** Time passes in the browser: the page polls and redraws what depends on it. */
    private void tick() {
        UI ui = UI.getCurrent();
        ComponentUtil.fireEvent(ui, new PollEvent(ui, true));
        runPendingSignalsTasks();
    }

    private Div detailPane() {
        return $(Div.class).withClassName("task-detail").single();
    }

    private ComponentQuery<Span> detailText(String className) {
        return $(Span.class).from(detailPane()).withClassName(className);
    }

    private String detail(String className) {
        return detailText(className).single().getText();
    }

    private ComponentQuery<Button> detailButton(String text) {
        return $(Button.class).from(detailPane()).withText(text);
    }

    private List<String> listedTaskNames() {
        return $(Div.class).withClassName("tasks-row").all().stream()
                .map(row -> $(Span.class).from(row).withClassName("task-name").single().getText())
                .toList();
    }
}
