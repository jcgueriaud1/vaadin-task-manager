package com.example.taskmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.internal.JacksonUtils;

/** The global "Enable notifications" button and the browser's notification permission. */
@SpringBootTest
@Import(TestTaskConfig.class)
class NotificationPermissionTest extends SpringBrowserlessTest {

    @BeforeEach
    void open() {
        navigate(ActionsView.class);
    }

    @Test
    void permissionIsRequestedOnlyOnClick() {
        List<PendingJavaScriptInvocation> beforeClick = pending("Notification");
        assertTrue(beforeClick.stream()
                .noneMatch(invocation -> invocation.getInvocation().getExpression().contains("requestPermission")));
        answer(beforeClick.getFirst(), "default");

        test(button()).click();

        assertEquals(1, pending("requestPermission").size());
    }

    @Test
    void theButtonReflectsThePermissionTheBrowserReports() {
        answer(pending("Notification.permission").getFirst(), "default");
        assertEquals("Enable notifications", button().getText());
        assertTrue(button().isEnabled());

        test(button()).click();
        answer(pending("requestPermission").getFirst(), "denied");

        assertEquals("Notifications blocked", button().getText());
        assertFalse(button().isEnabled());
    }

    @Test
    void theButtonStaysHiddenWithoutNotificationSupport() {
        answer(pending("Notification.permission").getFirst(), "unsupported");

        assertFalse($(Button.class).withAriaLabel("Notification permission").exists());
    }

    @Test
    void aGrantedPermissionNeedsNoButtonClick() {
        answer(pending("Notification.permission").getFirst(), "granted");

        assertEquals("Notifications on", button().getText());
        assertFalse(button().isEnabled());
    }

    private Button button() {
        return $(Button.class).withAriaLabel("Notification permission").single();
    }

    /** The JavaScript calls the tab would send whose code contains the text. */
    private static List<PendingJavaScriptInvocation> pending(String code) {
        return UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().stream()
                .filter(invocation -> invocation.getInvocation().getExpression().contains(code))
                .toList();
    }

    /** Simulates the browser returning a value from the JavaScript call. */
    private static void answer(PendingJavaScriptInvocation invocation, String value) {
        invocation.complete(JacksonUtils.getMapper().valueToTree(value));
    }
}
