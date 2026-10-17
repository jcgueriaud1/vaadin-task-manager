package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/** A Task that finishes while no tab of the session is visible is Unseen until a tab is visible. */
class UnseenTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList();

    @Test
    void aTaskThatFinishesWhileEveryTabIsHiddenIsUnseen() {
        TaskList.Tab tab = taskList.openTab();
        Task task = start("Generate report", ctx -> {});
        tab.setVisible(false);

        executor.runAll();

        assertTrue(task.unseen().peek());
        assertEquals(1, taskList.unseenCount().peek());
    }

    @Test
    void everyUnseenTaskIsSeenAsSoonAsAnyTabIsVisible() {
        TaskList.Tab first = taskList.openTab();
        TaskList.Tab second = taskList.openTab();
        Task generated = start("Generate report", ctx -> {});
        Task failed = start("Sync with server", ctx -> {
            throw new IllegalStateException("Server unreachable");
        });
        first.setVisible(false);
        second.setVisible(false);
        executor.runAll();
        assertEquals(2, taskList.unseenCount().peek());

        second.setVisible(true);

        assertFalse(generated.unseen().peek());
        assertFalse(failed.unseen().peek());
        assertEquals(0, taskList.unseenCount().peek());
    }

    @Test
    void aClosedTabNoLongerCountsAsVisible() {
        TaskList.Tab closed = taskList.openTab();
        TaskList.Tab hidden = taskList.openTab();
        hidden.setVisible(false);
        Task task = start("Generate report", ctx -> {});

        closed.close();
        executor.runAll();

        assertTrue(task.unseen().peek());
    }

    @Test
    void aTaskThatFinishesWhileATabIsVisibleIsNotUnseen() {
        TaskList.Tab hidden = taskList.openTab();
        taskList.openTab();
        hidden.setVisible(false);
        Task task = start("Generate report", ctx -> {});

        executor.runAll();

        assertFalse(task.unseen().peek());
        assertEquals(0, taskList.unseenCount().peek());
    }

    private Task start(String actionName, Consumer<TaskContext> work) {
        Task task = taskList.start(new Action(actionName, BlockingMode.NON_BLOCKING));
        executor.execute(() -> task.run(work));
        return task;
    }
}
