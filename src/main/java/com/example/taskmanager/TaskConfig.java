package com.example.taskmanager;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.AsyncProgressAction;
import com.example.taskmanager.task.BlockingMode;
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

    @Bean
    public DemoActions demoActions() {
        return new DemoActions(List.of(
                new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
                    ctx.setStatusText("Preparing recipients");
                    sleep(1500);
                    for (int batch = 1; batch <= 5; batch++) {
                        ctx.setStatusText("Sending emails: batch " + batch + " of 5");
                        sleep(1000);
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("Sent to 1,240 subscribers");
                    return 1240;
                }),
                new AsyncProgressAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> {
                    String[] steps = { "collecting data", "aggregating", "rendering charts", "writing PDF files" };
                    for (int step = 0; step < steps.length; step++) {
                        ctx.setStatusText("Step " + (step + 1) + " of " + steps.length + ": " + steps[step] + "…");
                        for (int tick = 1; tick <= 10; tick++) {
                            sleep(200);
                            ctx.setProgress((step + tick / 10.0) / steps.length);
                        }
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("5 PDF files");
                    return 5;
                }),
                new AsyncAction<>("Sync with server", BlockingMode.NON_BLOCKING, ctx -> {
                    ctx.setStatusText("Connecting");
                    sleep(1500);
                    ctx.setStatusText("Downloading changes");
                    sleep(2000);
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        throw new IllegalStateException("Server unreachable: connection timed out");
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("Up to date");
                    return null;
                })));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
