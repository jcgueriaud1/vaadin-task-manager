package org.vaadin.addons.taskpulse;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.signals.Signal;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;

/** Shows a toast in a tab when a Task of the session finishes. {@link PulseWatcher} sets them up. */
final class PulseToasts {

    private PulseToasts() {
    }

    /**
     * Shows the toasts for as long as the owner, which lives in the tab, is
     * attached. "Open" and "Details" go to the open handler, and are hidden
     * without one.
     */
    static void showIn(Component owner, PulseTaskList taskList, Signal<Optional<Consumer<PulseTask>>> openHandler) {
        // The effect runs again on every change to the list, so the tab remembers what it has shown
        Set<PulseTask> handled = new HashSet<>();
        Signal.effect(owner, () -> taskList.tasks().getValues()
                .forEach(task -> {
                    Optional<Outcome> outcome = task.outcome().get();
                    if (outcome.isEmpty() || !handled.add(task)) {
                        return;
                    }
                    Signal.untracked(() -> {
                        Notification toast = switch (outcome.get()) {
                            case Outcome.Succeeded succeeded -> succeeded(task, succeeded, openHandler);
                            case Outcome.Failed failed -> failed(task, failed, openHandler);
                            case Outcome.Cancelled cancelled -> null;
                        };
                        owner.getUI().filter(PulseToasts::visible).ifPresent(ui -> {
                            if (toast != null) {
                                // open() alone would attach the toast to whichever UI is current
                                ui.add(toast);
                                toast.open();
                            }
                        });
                        return null;
                    });
                }));
    }

    /** Whether the user can see the tab, even if another window has the focus. */
    private static boolean visible(UI ui) {
        PageVisibility visibility = ui.getPage().pageVisibilitySignal().peek();
        return visibility == PageVisibility.VISIBLE || visibility == PageVisibility.VISIBLE_NOT_FOCUSED;
    }

    private static Notification succeeded(PulseTask task, Outcome.Succeeded outcome,
            Signal<Optional<Consumer<PulseTask>>> openHandler) {
        Toast toast = new Toast();
        Span resultSummary = new Span(outcome.resultSummary());
        resultSummary.addClassName("pulse-result-summary");
        toast.add(icon(VaadinIcon.CHECK_CIRCLE, "succeeded"), text(task, resultSummary),
                openButton("Open", task, toast, openHandler), dismiss(toast));
        return toast;
    }

    private static Notification failed(PulseTask task, Outcome.Failed outcome,
            Signal<Optional<Consumer<PulseTask>>> openHandler) {
        Toast toast = new Toast();
        toast.addThemeVariants(NotificationVariant.ERROR);
        Span errorMessage = new Span(outcome.errorMessage());
        errorMessage.addClassName("pulse-error-message");
        toast.add(icon(VaadinIcon.EXCLAMATION_CIRCLE, "failed"), text(task, errorMessage),
                openButton("Details", task, toast, openHandler), dismiss(toast));
        return toast;
    }

    private static Component icon(VaadinIcon vaadinIcon, String state) {
        Icon icon = vaadinIcon.create();
        icon.addClassNames("pulse-state-icon", state);
        icon.getElement().setAttribute("aria-hidden", "true");
        return icon;
    }

    /** The Task's name in bold, then what it produced or why it failed. */
    private static Div text(PulseTask task, Span outcome) {
        Span name = new Span(task.action().name());
        name.addClassName("pulse-name");
        Div text = new Div(name, outcome);
        text.addClassName("pulse-toast-text");
        return text;
    }

    private static Button openButton(String text, PulseTask task, Notification toast,
            Signal<Optional<Consumer<PulseTask>>> openHandler) {
        Button open = new Button(text, e -> {
            openHandler.peek().ifPresent(handler -> handler.accept(task));
            toast.close();
        });
        open.addThemeVariants(ButtonVariant.TERTIARY);
        open.setVisible(openHandler.peek().isPresent());
        return open;
    }

    private static Button dismiss(Notification toast) {
        Button dismiss = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> toast.close());
        dismiss.addThemeVariants(ButtonVariant.TERTIARY);
        dismiss.setAriaLabel("Dismiss");
        return dismiss;
    }

    /** A toast that loads the add-on's stylesheet wherever it opens. */
    @StyleSheet(PulseTaskParts.STYLESHEET)
    private static final class Toast extends Notification {

        Toast() {
            addClassName("pulse-toast");
        }
    }
}
