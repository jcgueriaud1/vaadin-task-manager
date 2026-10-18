package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;

/** A Task that finishes while no tab of the session is visible is Unseen until a tab is visible. */
class UnseenTest {

    private final TestExecutor executor = new TestExecutor();
    private final PulseTaskList taskList = new PulseTaskList();
    private final PulseAction report = new PulseAction("Generate report", BlockingMode.NON_BLOCKING);
    private final PulseAction sync = new PulseAction("Sync with server", BlockingMode.NON_BLOCKING);

    @Test
    void aTaskThatFinishesWhileEveryTabIsHiddenIsUnseen() {
        PulseTaskList.Tab tab = taskList.openTab();
        PulseTask task = taskList.start(report, executor, ctx -> {});
        tab.setVisible(false);

        executor.runAll();

        assertTrue(task.unseen().peek());
        assertEquals(1, taskList.unseenCount().peek());
    }

    @Test
    void withoutAnyTabNothingIsUnseen() {
        PulseTask task = taskList.start(report, executor, ctx -> {});

        executor.runAll();

        assertFalse(task.unseen().peek());
        assertEquals(0, taskList.unseenCount().peek());
    }

    @Test
    void everyUnseenTaskIsSeenAsSoonAsAnyTabIsVisible() {
        PulseTaskList.Tab first = taskList.openTab();
        PulseTaskList.Tab second = taskList.openTab();
        PulseTask generated = taskList.start(report, executor, ctx -> {});
        PulseTask failed = taskList.start(sync, executor, ctx -> {
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
        PulseTaskList.Tab closed = taskList.openTab();
        PulseTaskList.Tab hidden = taskList.openTab();
        hidden.setVisible(false);
        PulseTask task = taskList.start(report, executor, ctx -> {});

        closed.close();
        executor.runAll();

        assertTrue(task.unseen().peek());
    }

    @Test
    void aTaskThatFinishesWhileATabIsVisibleIsNotUnseen() {
        PulseTaskList.Tab hidden = taskList.openTab();
        taskList.openTab();
        hidden.setVisible(false);
        PulseTask task = taskList.start(report, executor, ctx -> {});

        executor.runAll();

        assertFalse(task.unseen().peek());
        assertEquals(0, taskList.unseenCount().peek());
    }

}
