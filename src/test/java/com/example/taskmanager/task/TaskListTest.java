package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class TaskListTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList(executor);

    @Test
    void overallProgressAveragesRunningTasksThatReportProgress() {
        var quarter = new AsyncProgressAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
            return null;
        });
        var threeQuarters = new AsyncProgressAction<>("Export data", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setProgress(0.75);
            executor.pause();
            ctx.setProgress(1);
            return null;
        });
        var withoutProgress = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
            executor.pause();
            return null;
        });
        assertEquals(Optional.empty(), taskList.overallProgress().peek());

        withoutProgress.start(taskList);
        assertEquals(Optional.empty(), taskList.overallProgress().peek());

        threeQuarters.start(taskList);
        quarter.start(taskList);
        assertEquals(Optional.of(0.0), taskList.overallProgress().peek());

        executor.runNext(); // newsletter pauses
        executor.resume(); // newsletter succeeds
        executor.runNext(); // export at 0.75, report still at 0
        assertEquals(Optional.of(0.375), taskList.overallProgress().peek());

        executor.resume(); // export succeeds: it no longer counts
        executor.runNext(); // report at 0.25
        assertEquals(Optional.of(0.25), taskList.overallProgress().peek());

        executor.resume();
        assertEquals(Optional.empty(), taskList.overallProgress().peek());
    }
}
