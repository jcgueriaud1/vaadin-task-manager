package org.vaadin.addons.taskpulse.demo.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;
import org.vaadin.addons.taskpulse.PulseTask;

/**
 * Covers the tab that started a Blocking Task until it finishes. The user can't
 * close it; only Cancel or the Task finishing does.
 */
public class BlockingTaskDialog extends Dialog {

    /**
     * Opens a dialog over the current tab if the Task is Blocking. Every start of a
     * Task from a tab goes through here, Retry included.
     */
    public static void openIfBlocking(PulseTask task) {
        if (task.action().blockingMode() == BlockingMode.BLOCKING) {
            new BlockingTaskDialog(task).open();
        }
    }

    public BlockingTaskDialog(PulseTask task) {
        setModal(true);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setHeaderTitle(task.action().name());
        Span statusText = new Span();
        statusText.addClassName("task-status-text");
        statusText.bindText(task.statusText());
        add(TaskTray.progress(task), statusText);
        getFooter().add(new Button("Cancel", e -> task.cancel()));
        Signal.effect(this, () -> {
            if (!task.running().get()) {
                close();
            }
        });
    }
}
