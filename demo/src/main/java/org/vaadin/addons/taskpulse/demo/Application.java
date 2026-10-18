package org.vaadin.addons.taskpulse.demo;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import org.vaadin.addons.taskpulse.demo.ui.DemoTheme;

/**
 * The entry point of the Spring Boot application.
 * <p>
 * {@code @Push} lets Task updates made on background threads reach the browser
 * right away. The theme is the one chosen in the theme switcher, or Aura.
 */
@SpringBootApplication
@PWA(name = "TaskPulse Demo", shortName = "TaskPulse")
@Push
@StyleSheet("styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        DemoTheme theme = DemoTheme.of(settings.getRequest());
        settings.addLink(theme.stylesheet(), Map.of("rel", "stylesheet", "id", DemoTheme.LINK_ID));
        if (!theme.htmlTheme().isEmpty()) {
            settings.addInlineWithContents(
                    "document.documentElement.setAttribute('theme', '" + theme.htmlTheme() + "');",
                    Inline.Wrapping.JAVASCRIPT);
        }
    }
}
