package com.example.taskmanager.ui;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.taskmanager.DemoActions;
import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.AsyncProgressAction;
import com.example.taskmanager.task.BlockingMode;
import com.example.taskmanager.task.TestExecutor;

/** Replaces the Executor and the demo Actions with ones the test drives. */
@TestConfiguration
public class TestTaskConfig {

    @Bean
    @Primary
    public TestExecutor testExecutor() {
        return new TestExecutor();
    }

    @Bean
    @Primary
    public DemoActions testActions(TestExecutor executor) {
        return new DemoActions(List.of(
                new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
                    ctx.setStatusText("Preparing recipients");
                    executor.pause();
                    ctx.setStatusText("Sending emails");
                    executor.pause();
                    return null;
                }),
                new AsyncAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> null),
                new AsyncProgressAction<>("Export data", BlockingMode.NON_BLOCKING, ctx -> {
                    ctx.setProgress(0.5);
                    executor.pause();
                    ctx.setProgress(1);
                    ctx.setResultSummary("3 CSV files");
                    return null;
                }),
                new AsyncAction<>("Sync with server", BlockingMode.NON_BLOCKING, ctx -> {
                    throw new IllegalStateException("Server unreachable");
                }),
                new AsyncProgressAction<>("Import data", BlockingMode.BLOCKING, ctx -> {
                    ctx.setStatusText("Reading file 1");
                    ctx.setProgress(0.25);
                    executor.pause();
                    ctx.setStatusText("Reading file 2");
                    ctx.setProgress(0.75);
                    executor.pause();
                    return null;
                }),
                new AsyncAction<>("Restore backup", BlockingMode.BLOCKING, ctx -> {
                    ctx.setStatusText("Checking backup");
                    executor.pause();
                    throw new IllegalStateException("Backup is corrupt");
                })));
    }
}
