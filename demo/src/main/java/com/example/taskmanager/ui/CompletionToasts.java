package com.example.taskmanager.ui;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.example.taskmanager.task.Outcome;
import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.signals.Signal;

/** Shows a toast in a tab when a Task of the session finishes. */
final class CompletionToasts {

    private CompletionToasts() {
    }

    /** Shows the toasts for as long as the owner, which lives in the tab, is attached. */
    static void showIn(Component owner, TaskList taskList) {
        // The effect runs again on every change to the list, so the tab remembers what it has shown
        Set<Task> handled = new HashSet<>();
        Signal.effect(owner, () -> taskList.tasks().getValues()
                .forEach(task -> {
                    Optional<Outcome> outcome = task.outcome().get();
                    if (outcome.isEmpty() || !handled.add(task)) {
                        return;
                    }
                    Signal.untracked(() -> {
                        Notification toast = switch (outcome.get()) {
                            case Outcome.Succeeded succeeded -> succeeded(task, succeeded);
                            case Outcome.Failed failed -> failed(task, failed);
                            case Outcome.Cancelled cancelled -> null;
                        };
                        owner.getUI().filter(CompletionToasts::visible).ifPresent(ui -> {
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

    private static Notification succeeded(Task task, Outcome.Succeeded outcome) {
        Notification toast = new Notification();
        toast.addClassName("completion-toast");
        Span resultSummary = new Span(outcome.resultSummary());
        resultSummary.addClassName("task-result-summary");
        Button open = new Button("Open", e -> openDetails(e.getSource(), task, toast));
        open.addThemeVariants(ButtonVariant.TERTIARY);
        toast.add(icon(VaadinIcon.CHECK_CIRCLE, "succeeded"), text(task, resultSummary), open, dismiss(toast));
        return toast;
    }

    private static Notification failed(Task task, Outcome.Failed outcome) {
        Notification toast = new Notification();
        toast.addClassName("completion-toast");
        toast.addThemeVariants(NotificationVariant.ERROR);
        Span errorMessage = new Span(outcome.errorMessage());
        errorMessage.addClassName("task-error-message");
        Button details = new Button("Details", e -> openDetails(e.getSource(), task, toast));
        details.addThemeVariants(ButtonVariant.TERTIARY);
        toast.add(icon(VaadinIcon.EXCLAMATION_CIRCLE, "failed"), text(task, errorMessage), details, dismiss(toast));
        return toast;
    }

    private static Component icon(VaadinIcon vaadinIcon, String state) {
        Icon icon = vaadinIcon.create();
        icon.addClassNames("task-state-icon", state);
        icon.getElement().setAttribute("aria-hidden", "true");
        return icon;
    }

    /** The Task's name in bold, then what it produced or why it failed. */
    private static Div text(Task task, Span outcome) {
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Div text = new Div(name, outcome);
        text.addClassName("completion-toast-text");
        return text;
    }

    private static Button dismiss(Notification toast) {
        Button dismiss = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> toast.close());
        dismiss.addThemeVariants(ButtonVariant.TERTIARY);
        dismiss.setAriaLabel("Dismiss");
        return dismiss;
    }

    private static void openDetails(Component source, Task task, Notification toast) {
        source.getUI().ifPresent(ui -> ui.navigate(TasksView.class,
                new RouteParameters(TasksView.TASK_ID, String.valueOf(task.id()))));
        toast.close();
    }
}
