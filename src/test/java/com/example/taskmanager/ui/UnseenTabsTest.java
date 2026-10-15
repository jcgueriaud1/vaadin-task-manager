package com.example.taskmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.example.taskmanager.task.TestExecutor;
import com.vaadin.browserless.ComponentQuery;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.server.VaadinSession;

/** Two tabs of one session, hidden and shown, count Unseen Tasks in their title and task center. */
@SpringBootTest
@Import(TestTaskConfig.class)
class UnseenTabsTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    private UI first;
    private UI second;

    @BeforeEach
    void openTwoTabs() {
        navigate(ActionsView.class);
        first = UI.getCurrent();
        second = new UI();
        second.getInternals().setSession(VaadinSession.getCurrent());
        UI.setCurrent(second);
        second.navigate(TasksView.class);
        UI.setCurrent(first);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void everyTabTitleCountsUnseenTasksUntilATabIsVisible() {
        test(startButton("Generate report")).click();
        hide(first);
        hide(second);
        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("(1) Actions", title(first));
        assertEquals("(1) Background tasks", title(second));

        setVisibility(second, PageVisibility.VISIBLE_NOT_FOCUSED);

        assertEquals("Actions", title(first));
        assertEquals("Background tasks", title(second));
    }

    @Test
    void everyTaskCenterShowsAnUnseenBadgeUntilATabIsVisible() {
        test(startButton("Generate report")).click();
        test(startButton("Sync with server")).click();
        hide(first);
        hide(second);
        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("2", unseenBadge(first).single().getText());
        assertEquals("2", unseenBadge(second).single().getText());

        setVisibility(first, PageVisibility.VISIBLE);

        assertFalse(unseenBadge(first).exists());
        assertFalse(unseenBadge(second).exists());
    }

    private ComponentQuery<Span> unseenBadge(UI ui) {
        MainLayout layout = (MainLayout) ui.getInternals().getActiveRouterTargetsChain().stream()
                .filter(MainLayout.class::isInstance)
                .findFirst()
                .orElseThrow();
        return $(Span.class).from(layout).withAttribute("aria-label", "Unseen Tasks");
    }

    private void hide(UI ui) {
        setVisibility(ui, PageVisibility.HIDDEN);
    }

    /** Simulates the browser reporting the tab's page visibility in a request of its own. */
    private void setVisibility(UI ui, PageVisibility visibility) {
        var data = JacksonUtils.createObjectNode();
        data.put("event.detail", visibility.name());
        data.put("for", "trailing"); // The listener is debounced
        ui.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(ui.getElement(), "vaadin-page-visibility-change", data));
        runPendingSignalsTasks();
    }

    /** The title the browser shows after the tab's next server response. */
    private String title(UI ui) {
        UI current = UI.getCurrent();
        UI.setCurrent(ui);
        try {
            roundTrip();
            return ui.getInternals().getTitle();
        } finally {
            UI.setCurrent(current);
        }
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
