package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.Test;

/** A Task that finishes while no tab of the session is visible is Unseen until a tab is visible. */
class UnseenTest {

    private final TestExecutor executor = new TestExecutor();
    private final TaskList taskList = new TaskList();
    private final Action report = new Action("Generate report", BlockingMode.NON_BLOCKING);
    private final Action sync = new Action("Sync with server", BlockingMode.NON_BLOCKING);

    @Test
    void aTaskThatFinishesWhileEveryTabIsHiddenIsUnseen() {
        TaskList.Tab tab = taskList.openTab();
        Task task = taskList.start(report, executor, ctx -> {});
        tab.setVisible(false);

        executor.runAll();

        assertTrue(task.unseen().peek());
        assertEquals(1, taskList.unseenCount().peek());
    }

    @Test
    void everyUnseenTaskIsSeenAsSoonAsAnyTabIsVisible() {
        TaskList.Tab first = taskList.openTab();
        TaskList.Tab second = taskList.openTab();
        Task generated = taskList.start(report, executor, ctx -> {});
        Task failed = taskList.start(sync, executor, ctx -> {
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
        Task task = taskList.start(report, executor, ctx -> {});

        closed.close();
        executor.runAll();

        assertTrue(task.unseen().peek());
    }

    @Test
    void aTaskThatFinishesWhileATabIsVisibleIsNotUnseen() {
        TaskList.Tab hidden = taskList.openTab();
        taskList.openTab();
        hidden.setVisible(false);
        Task task = taskList.start(report, executor, ctx -> {});

        executor.runAll();

        assertFalse(task.unseen().peek());
        assertEquals(0, taskList.unseenCount().peek());
    }

}
