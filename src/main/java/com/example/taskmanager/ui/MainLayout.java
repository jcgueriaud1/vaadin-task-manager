package com.example.taskmanager.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/**
 * The app shell: a navbar and the Task tray, present in every view. It reports
 * whether its browser tab is visible, counts Unseen Tasks in the page title and
 * shows a native notification for each Task that becomes Unseen.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final TaskList taskList;
    private final ValueSignal<String> viewTitle = new ValueSignal<>("");
    private final Set<Long> notified = new HashSet<>();
    private TaskList.Tab tab;

    public MainLayout(TaskList taskList) {
        this.taskList = taskList;
        H1 title = new H1("Task Manager");
        title.addClassName("app-title");
        addToNavbar(title, new TaskCenter(taskList), new NotificationPermissionButton());
        getElement().appendChild(new TaskTray(taskList).getElement());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        boolean firstAttach = tab == null;
        tab = taskList.openTab();
        super.onAttach(attachEvent);
        if (firstAttach) {
            // Effects owned by this layout pause while it is detached
            UI ui = attachEvent.getUI();
            Signal.effect(this, () -> {
                PageVisibility visibility = ui.getPage().pageVisibilitySignal().get();
                tab.setVisible(visibility != PageVisibility.HIDDEN);
            });
            Signal.effect(this, () -> {
                int unseen = taskList.unseenCount().get();
                String base = viewTitle.get();
                String pageTitle = unseen > 0 ? "(" + unseen + ") " + base : base;
                // After the router, which sets the plain view title once navigation ends
                ui.beforeClientResponse(this, context -> ui.getPage().setTitle(pageTitle));
            });
            Signal.effect(this, () -> {
                List<Task> unseen = taskList.tasks().getValues()
                        .filter(task -> task.unseen().get())
                        .toList();
                Signal.untracked(() -> {
                    unseen.stream()
                            .filter(task -> notified.add(task.id()))
                            .forEach(task -> notify(ui, task));
                    notified.retainAll(unseen.stream().map(Task::id).toList());
                    return null;
                });
            });
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        tab.close();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        viewTitle.set(titleOf(getContent()));
    }

    /**
     * Shows a native notification if the user granted permission. Every hidden tab
     * asks; the browser shows one, because the Task id tags it.
     */
    private static void notify(UI ui, Task task) {
        ui.getPage().executeJs("""
                if (!('Notification' in window) || Notification.permission !== 'granted') {
                    return;
                }
                const notification = new Notification($0, { body: $1, tag: $2 });
                notification.onclick = () => {
                    window.focus();
                    notification.close();
                };
                """, task.action().name(), body(task), String.valueOf(task.id()));
    }

    /** The Result summary of a Succeeded Task, the error of a Failed one. */
    private static String body(Task task) {
        return switch (task.state().peek()) {
            case SUCCEEDED -> task.resultSummary().peek().isEmpty() ? "Succeeded" : task.resultSummary().peek();
            case FAILED -> task.errorMessage().peek();
            case CANCELLED -> "Cancelled";
            case RUNNING -> throw new IllegalStateException("A Running Task is never Unseen");
        };
    }

    private static String titleOf(Component view) {
        if (view instanceof HasDynamicTitle dynamic) {
            return dynamic.getPageTitle();
        }
        PageTitle title = view.getClass().getAnnotation(PageTitle.class);
        return title != null ? title.value() : "";
    }
}
