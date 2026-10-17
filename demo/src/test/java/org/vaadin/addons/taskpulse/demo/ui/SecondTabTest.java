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
import com.vaadin.flow.server.VaadinSession;
import org.vaadin.addons.taskpulse.PulseTaskList;
import org.vaadin.addons.taskpulse.TestExecutor;
import org.vaadin.addons.taskpulse.PulseTray;

/** Two browser tabs of one session see the same Running count and Dismissals. */
@SpringBootTest
@Import(TestTaskConfig.class)
class SecondTabTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @Autowired
    private ApplicationContext context;

    private UI second;
    private MainLayout secondTab;

    @BeforeEach
    void openTwoTabs() {
        navigate(TestActionsView.class);
        UI first = UI.getCurrent();
        second = new UI();
        second.getInternals().setSession(VaadinSession.getCurrent());
        // Vaadin builds a tab's components while that tab's UI is current
        UI.setCurrent(second);
        secondTab = new MainLayout(context.getBean(PulseTaskList.class));
        second.add(secondTab);
        UI.setCurrent(first);
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void runningCountAndDismissShowInTheOtherTab() {
        test(startButton("Generate report")).click();
        runPendingSignalsTasks();
        assertEquals("1 running", $(Button.class).from(secondTab).withAriaLabel("Task center").single().getText());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("Tasks", $(Button.class).from(secondTab).withAriaLabel("Task center").single().getText());
        assertTrue(secondTrayShown());

        test($(Button.class).withAriaLabel("Dismiss Generate report").single()).click();
        runPendingSignalsTasks();
        assertFalse(secondTrayShown());
    }

    private boolean secondTrayShown() {
        // The tray belongs to the tab's UI, not to its layout
        return $(PulseTray.class).from(second).exists();
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
