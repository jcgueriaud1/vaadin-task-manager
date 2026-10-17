package org.vaadin.addons.taskpulse.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.aura.Aura;

/**
 * The entry point of the Spring Boot application.
 * <p>
 * {@code @Push} lets Task updates made on background threads reach the browser
 * right away.
 */
@SpringBootApplication
@PWA(name = "TaskPulse Demo", shortName = "TaskPulse")
@Push
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
