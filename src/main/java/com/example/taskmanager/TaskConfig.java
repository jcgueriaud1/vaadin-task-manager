package com.example.taskmanager;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

@Configuration
public class TaskConfig {

    /** Runs the work of every Task on its own virtual thread. */
    @Bean
    public ExecutorService taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Where Tasks take their start and finish times from. Tests replace this bean. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** The Task list of the current session, shared by all its browser tabs (ADR 0001). */
    @Bean
    @VaadinSessionScope
    public TaskList taskList(Executor executor, Clock clock) {
        return new TaskList(executor, clock);
    }
}
