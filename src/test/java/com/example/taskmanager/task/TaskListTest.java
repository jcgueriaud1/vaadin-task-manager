package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

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
}
