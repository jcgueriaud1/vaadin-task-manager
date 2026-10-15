package com.example.taskmanager.ui;

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
 * whether its browser tab is visible and counts Unseen Tasks in the page title.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final TaskList taskList;
    private final ValueSignal<String> viewTitle = new ValueSignal<>("");
    private TaskList.Tab tab;

    public MainLayout(TaskList taskList) {
        this.taskList = taskList;
        H1 title = new H1("Task Manager");
        title.addClassName("app-title");
        addToNavbar(title, new TaskCenter(taskList));
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

    private static String titleOf(Component view) {
        if (view instanceof HasDynamicTitle dynamic) {
            return dynamic.getPageTitle();
        }
        PageTitle title = view.getClass().getAnnotation(PageTitle.class);
        return title != null ? title.value() : "";
    }
}
