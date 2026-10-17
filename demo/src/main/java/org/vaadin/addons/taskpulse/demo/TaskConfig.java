package org.vaadin.addons.taskpulse.demo;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.vaadin.addons.taskpulse.PulseTaskList;

@Configuration
public class TaskConfig {

    /**
     * Where the Actions page runs the work of its Tasks: each on its own virtual
     * thread. The Task list doesn't use it; tests replace it to step through work.
     */
    @Bean
    public ExecutorService taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Where Tasks take their start and finish times from. Tests replace this bean. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * The Task list of the current session, shared by all its browser tabs. It is
     * installed in the session with this app's clock, so it is also the one that
     * {@link PulseTaskList#getCurrent()} returns (ADR 0001, ADR 0003).
     */
    @Bean
    @VaadinSessionScope
    public PulseTaskList taskList(Clock clock) {
        return PulseTaskList.install(VaadinSession.getCurrent(), new PulseTaskList(clock));
    }
}
