package com.example.taskmanager.ui;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.taskmanager.task.Action;
import com.example.taskmanager.task.BlockingMode;
import com.example.taskmanager.task.TestClock;
import com.example.taskmanager.task.TestExecutor;

/**
 * Replaces the Executor and the Clock with ones the test drives, and provides
 * the Actions that {@link TestActionsView} shows instead of the demo ones.
 */
@TestConfiguration
public class TestTaskConfig {

    @Bean
    @Primary
    public TestExecutor testExecutor() {
        return new TestExecutor();
    }

    @Bean
    @Primary
    public TestClock testClock() {
        return new TestClock(Instant.parse("2026-10-15T09:00:00Z"));
    }

    /** The Actions the tests start, with their work. */
    public record TestActions(List<ActionsView.Demo> demos) {
    }

    @Bean
    public TestActions testActions(TestExecutor executor) {
        return new TestActions(List.of(
                new ActionsView.Demo(new Action("Send newsletter", BlockingMode.NON_BLOCKING), ctx -> {
                    ctx.setStatusText("Preparing recipients");
                    executor.pause();
                    ctx.setStatusText("Sending emails");
                    executor.pause();
                }),
                new ActionsView.Demo(new Action("Generate report", BlockingMode.NON_BLOCKING), ctx -> {}),
                new ActionsView.Demo(new Action("Export data", BlockingMode.NON_BLOCKING), ctx -> {
                    ctx.setProgress(0.5);
                    executor.pause();
                    ctx.setProgress(1);
                    ctx.setResultSummary("3 CSV files");
                }),
                new ActionsView.Demo(new Action("Sync with server", BlockingMode.NON_BLOCKING), ctx -> {
                    throw new IllegalStateException("Server unreachable");
                }),
                new ActionsView.Demo(new Action("Import data", BlockingMode.BLOCKING), ctx -> {
                    ctx.setStatusText("Reading file 1");
                    ctx.setProgress(0.25);
                    executor.pause();
                    ctx.setStatusText("Reading file 2");
                    ctx.setProgress(0.75);
                    executor.pause();
                }),
                new ActionsView.Demo(new Action("Restore backup", BlockingMode.BLOCKING), ctx -> {
                    ctx.setStatusText("Checking backup");
                    executor.pause();
                    throw new IllegalStateException("Backup is corrupt");
                })));
    }
}
