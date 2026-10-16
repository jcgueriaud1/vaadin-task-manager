package com.example.taskmanager.ui;

import java.util.HashSet;
import java.util.Set;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.example.taskmanager.task.TaskState;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
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
                    TaskState state = task.state().get();
                    if (state == TaskState.RUNNING || !handled.add(task)) {
                        return;
                    }
                    Signal.untracked(() -> {
                        Notification toast = switch (state) {
                            case SUCCEEDED -> succeeded(task);
                            case FAILED -> failed(task);
                            default -> null;
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

    private static Notification succeeded(Task task) {
        Notification toast = new Notification();
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Span resultSummary = new Span(task.resultSummary().peek());
        resultSummary.addClassName("task-result-summary");
        Button dismiss = new Button("Dismiss", e -> toast.close());
        dismiss.addThemeVariants(ButtonVariant.TERTIARY);
        toast.add(name, resultSummary, dismiss);
        return toast;
    }

    private static Notification failed(Task task) {
        Notification toast = new Notification();
        toast.addThemeVariants(NotificationVariant.ERROR);
        Span name = new Span(task.action().name());
        name.addClassName("task-name");
        Span errorMessage = new Span(task.errorMessage().peek());
        errorMessage.addClassName("task-error-message");
        Button details = new Button("Details", e -> {
            e.getSource().getUI().ifPresent(ui -> ui.navigate(TasksView.class,
                    new RouteParameters(TasksView.TASK_ID, String.valueOf(task.id()))));
            toast.close();
        });
        details.addThemeVariants(ButtonVariant.TERTIARY);
        toast.add(name, errorMessage, details);
        return toast;
    }
}
