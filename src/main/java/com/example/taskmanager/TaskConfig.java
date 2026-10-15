package com.example.taskmanager;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.taskmanager.task.AsyncAction;
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

    /** The Task list of the current session, shared by all its browser tabs (ADR 0001). */
    @Bean
    @VaadinSessionScope
    public TaskList taskList(Executor executor) {
        return new TaskList(executor);
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
                    ctx.setStatusText("Sent to 1,240 subscribers");
                    return 1240;
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
