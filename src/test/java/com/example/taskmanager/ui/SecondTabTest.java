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

import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TestExecutor;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.VaadinSession;

/** Two browser tabs of one session see the same Running count and Dismissals. */
@SpringBootTest
@Import(TestTaskConfig.class)
class SecondTabTest extends SpringBrowserlessTest {

    @Autowired
    private TestExecutor executor;

    @Autowired
    private ApplicationContext context;

    private MainLayout secondTab;

    @BeforeEach
    void openTwoTabs() {
        navigate(TestActionsView.class);
        UI first = UI.getCurrent();
        UI second = new UI();
        second.getInternals().setSession(VaadinSession.getCurrent());
        secondTab = new MainLayout(context.getBean(TaskList.class));
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
        assertEquals("1", $(Button.class).from(secondTab).withAriaLabel("Task center").single().getText());

        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("0", $(Button.class).from(secondTab).withAriaLabel("Task center").single().getText());
        assertTrue(secondTrayShown());

        test($(Button.class).withAriaLabel("Dismiss Generate report").single()).click();
        runPendingSignalsTasks();
        assertFalse(secondTrayShown());
    }

    private boolean secondTrayShown() {
        return $(TaskTray.class).from(secondTab).exists();
    }

    private Button startButton(String actionName) {
        return $(Button.class).withAriaLabel("Start " + actionName).single();
    }
}
