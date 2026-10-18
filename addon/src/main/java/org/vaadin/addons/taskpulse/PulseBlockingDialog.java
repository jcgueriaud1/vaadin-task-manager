package org.vaadin.addons.taskpulse;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;

/**
 * Covers the tab that started a Blocking Task until it finishes. The user can't
 * close it; only Cancel or the Task finishing does. The Task list opens it when
 * a Blocking Task starts from a tab.
 */
@StyleSheet(PulseTaskParts.STYLESHEET)
class PulseBlockingDialog extends Dialog {

    PulseBlockingDialog(PulseTask task) {
        addClassName("pulse-blocking-dialog");
        setModal(true);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        setHeaderTitle(task.action().name());
        Span statusText = new Span();
        statusText.addClassName("pulse-status-text");
        statusText.bindText(task.statusText());
        add(PulseTaskParts.progressBar(task), statusText);
        getFooter().add(new Button("Cancel", e -> task.cancel()));
        Signal.effect(this, () -> {
            if (!task.running().get()) {
                close();
            }
        });
    }
}
