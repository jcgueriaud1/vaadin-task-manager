package org.vaadin.addons.taskpulse.demo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.select.Select;

/** The theme switcher in the navbar. */
@SpringBootTest
@Import(TestTaskConfig.class)
class ThemeSwitcherTest extends SpringBrowserlessTest {

    @BeforeEach
    void open() {
        navigate(TestActionsView.class);
    }

    @Test
    void offersEveryThemeAndStartsOnAura() {
        @SuppressWarnings("unchecked")
        Select<DemoTheme> switcher = $(Select.class).withAriaLabel("Theme").single();

        assertEquals(DemoTheme.AURA, switcher.getValue());
        assertEquals(List.of("Lumo light", "Lumo dark", "Aura", "Custom (Aura)"),
                test(switcher).getSuggestions());
    }

    @Test
    void choosingAThemeSwapsTheStylesheetInTheBrowser() {
        @SuppressWarnings("unchecked")
        Select<DemoTheme> switcher = $(Select.class).withAriaLabel("Theme").single();

        test(switcher).selectItem("Lumo dark");

        List<PendingJavaScriptInvocation> invocations = UI.getCurrent().getInternals()
                .dumpPendingJavaScriptInvocations();
        assertTrue(invocations.stream()
                .map(invocation -> invocation.getInvocation().getParameters())
                .anyMatch(parameters -> parameters.contains("lumo/lumo.css") && parameters.contains("dark")));
    }
}
