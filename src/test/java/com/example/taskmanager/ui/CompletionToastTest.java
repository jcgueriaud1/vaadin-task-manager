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

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TestExecutor;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.dom.DebouncePhase;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.server.VaadinSession;

import tools.jackson.databind.node.ObjectNode;

/** When a Task finishes, every visible tab of the session shows a toast. */
@SpringBootTest
@Import(TestTaskConfig.class)
class CompletionToastTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void open() {
        navigate(ActionsView.class);
        setVisibility(UI.getCurrent(), PageVisibility.VISIBLE);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void aSucceededTaskShowsAToastWithItsNameAndResultSummary() {
        test(startButton("Export data")).click();
        executor.runAll();
        runPendingSignalsTasks();

        Notification toast = $(Notification.class).single();
        String text = toast.getElement().getTextRecursively();
        assertTrue(text.contains("Export data"), text);
        assertTrue(text.contains("3 CSV files"), text);
        assertTrue($(Button.class).from(toast).withText("Dismiss").exists());
    }

    @Test
    void aFailedTaskShowsAnErrorToastWithItsNameErrorMessageAndDetails() {
        test(startButton("Sync with server")).click();
        executor.runAll();
        runPendingSignalsTasks();

        Notification toast = $(Notification.class).single();
        String text = toast.getElement().getTextRecursively();
        assertTrue(text.contains("Sync with server"), text);
        assertTrue(text.contains("Server unreachable"), text);
        assertTrue(toast.hasThemeName(NotificationVariant.ERROR.getVariantName()));
        assertTrue($(Button.class).from(toast).withText("Details").exists());
    }

    @Test
    void detailsOpensTheFailedTaskOnTheBackgroundTasksPage() {
        test(startButton("Sync with server")).click();
        executor.runAll();
        runPendingSignalsTasks();
        Task failed = context.getBean(TaskList.class).tasks().peekValues().findFirst().orElseThrow();

        test($(Button.class).from($(Notification.class).single()).withText("Details").single()).click();

        assertEquals("tasks/" + failed.id(), UI.getCurrent().getActiveViewLocation().getPath());
        Div detailPane = $(Div.class).withClassName("task-detail").single();
        assertEquals("Sync with server", $(H2.class).from(detailPane).single().getText());
    }

    @Test
    void aCancelledTaskShowsNoToast() {
        test(startButton("Send newsletter")).click();
        executor.runNext();
        runPendingSignalsTasks();

        test($(Button.class).withAriaLabel("Cancel Send newsletter").single()).click();
        executor.resume();
        runPendingSignalsTasks();

        String trayText = $(TaskTray.class).single().getElement().getTextRecursively();
        assertTrue(trayText.contains("Cancelled"), trayText);
        assertFalse($(Notification.class).exists());
    }

    @Test
    void aHiddenTabShowsNoToast() {
        setVisibility(UI.getCurrent(), PageVisibility.HIDDEN);

        test(startButton("Generate report")).click();
        executor.runAll();
        runPendingSignalsTasks();

        assertFalse($(Notification.class).exists());
    }

    @Test
    void aTabWhoseVisibilityIsUnknownShowsNoToast() {
        setVisibility(UI.getCurrent(), PageVisibility.UNKNOWN);

        test(startButton("Generate report")).click();
        executor.runAll();
        runPendingSignalsTasks();

        assertFalse($(Notification.class).exists());
    }

    @Test
    void everyVisibleTabShowsTheToastEvenWithoutFocus() {
        UI starting = UI.getCurrent();
        UI unfocused = openTab(PageVisibility.VISIBLE_NOT_FOCUSED);
        UI hidden = openTab(PageVisibility.HIDDEN);

        test(startButton("Export data")).click();
        executor.runAll();
        runPendingSignalsTasks();

        assertTrue($(Notification.class).from(starting).exists());
        String text = $(Notification.class).from(unfocused).single().getElement().getTextRecursively();
        assertTrue(text.contains("3 CSV files"), text);
        assertFalse($(Notification.class).from(hidden).exists());
    }

    @Test
    void laterChangesToTheTaskListDoNotShowTheToastAgain() {
        test(startButton("Generate report")).click();
        executor.runAll();
        runPendingSignalsTasks();
        assertEquals(1, toastsAbout("Generate report"));

        test(startButton("Send newsletter")).click();
        executor.runNext();
        runPendingSignalsTasks();
        test($(Button.class).withAriaLabel("Dismiss Generate report").single()).click();
        runPendingSignalsTasks();
        navigate(TasksView.class);
        runPendingSignalsTasks();

        assertEquals(1, toastsAbout("Generate report"));
    }

    @Test
    void aTabOpenedAfterTheTaskFinishedShowsNoToastForIt() {
        test(startButton("Generate report")).click();
        executor.runAll();
        runPendingSignalsTasks();

        UI reloaded = openTab(PageVisibility.VISIBLE);
        test(startButton("Send newsletter")).click();
        executor.runNext();
        runPendingSignalsTasks();

        assertFalse($(Notification.class).from(reloaded).exists());
    }

    @Test
    void aBlockingTaskShowsItsToastOnceAfterItsModalCloses() {
        test(startButton("Import data")).click();
        executor.runNext();
        runPendingSignalsTasks();
        assertTrue($(Dialog.class).exists());

        executor.runAll();
        runPendingSignalsTasks();

        assertFalse($(Dialog.class).exists());
        assertEquals(1, toastsAbout("Import data"));
        Notification toast = $(Notification.class).single();
        test($(Button.class).from(toast).withText("Dismiss").single()).click();
        assertFalse(toast.isOpened());
    }

    private int toastsAbout(String actionName) {
        return $(Notification.class)
                .withCondition(toast -> toast.getElement().getTextRecursively().contains(actionName))
                .all().size();
    }

    /** Opens a new UI of the current session with the given visibility, and keeps the current UI current. */
    private UI openTab(PageVisibility visibility) {
        UI current = UI.getCurrent();
        UI tab = new UI();
        tab.getInternals().setSession(VaadinSession.getCurrent());
        // The browser reports visibility when the tab loads, before any view is shown
        setVisibility(tab, visibility);
        tab.add(new MainLayout(context.getBean(TaskList.class)));
        UI.setCurrent(current);
        return tab;
    }

    /**
     * Sends what the browser sends when the tab's visibility changes: the event
     * Page listens to, at the end of its debounce.
     */
    private static void setVisibility(UI ui, PageVisibility visibility) {
        ObjectNode data = JacksonUtils.createObjectNode();
        data.put("event.detail", visibility.name());
        data.put("for", DebouncePhase.TRAILING.getIdentifier());
        ui.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(ui.getElement(), "vaadin-page-visibility-change", data));
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
