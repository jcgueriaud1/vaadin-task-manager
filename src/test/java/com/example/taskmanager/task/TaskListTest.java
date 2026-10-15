package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TaskListTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList(executor);
    private final AsyncAction<String> newsletter =
            new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> "sent");
    private final AsyncAction<String> report =
            new AsyncAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> "report");

    @Test
    void dismissedTaskLeavesTheTrayButStaysInTheTaskList() {
        newsletter.start(taskList);
        report.start(taskList);
        executor.runAll();
        Task sent = task(0);
        Task generated = task(1);

        taskList.dismiss(sent);

        assertEquals(List.of(generated), taskList.trayTasks().peek());
        assertEquals(List.of(sent, generated), taskList.tasks().peekValues().toList());
    }

    @Test
    void aRunningTaskCannotBeDismissed() {
        newsletter.start(taskList);
        Task running = task(0);

        assertThrows(IllegalStateException.class, () -> taskList.dismiss(running));
        assertEquals(List.of(running), taskList.trayTasks().peek());
    }

    @Test
    void runningCountFollowsTasksStartingAndFinishing() {
        assertEquals(0, taskList.runningCount().peek());

        newsletter.start(taskList);
        report.start(taskList);
        assertEquals(2, taskList.runningCount().peek());

        executor.runNext();
        assertEquals(1, taskList.runningCount().peek());

        executor.runAll();
        assertEquals(0, taskList.runningCount().peek());
    }

    private Task task(int index) {
        return taskList.tasks().peekValues().toList().get(index);
    }

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
