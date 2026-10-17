package org.vaadin.addons.taskpulse.demo.ui;

import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouteParameters;
import org.vaadin.addons.taskpulse.PulseCenter;
import org.vaadin.addons.taskpulse.PulseNotificationButton;
import org.vaadin.addons.taskpulse.PulseTask;
import org.vaadin.addons.taskpulse.PulseTaskList;
import org.vaadin.addons.taskpulse.PulseTray;
import org.vaadin.addons.taskpulse.PulseWatcher;

/**
 * The app shell: a drawer with the app's pages, a navbar with the view title,
 * the notification button and the task center, and the Task tray, present in
 * every view. Its {@link PulseWatcher} does the rest for each tab: the Unseen
 * count and the overall Progress in the page title, native notifications and
 * completion toasts. Every "Open" leads to the Task on the Background tasks page.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final H1 title = new H1();

    public MainLayout(PulseTaskList taskList) {
        Span appName = new Span("TaskPulse");
        appName.addClassName("app-name");
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Actions", ActionsView.class, VaadinIcon.PLAY_CIRCLE_O.create()),
                new SideNavItem("Background tasks", TasksView.class, VaadinIcon.TASKS.create()));
        addToDrawer(appName, nav);

        Consumer<PulseTask> openDetails = task -> UI.getCurrent().navigate(TasksView.class,
                new RouteParameters(TasksView.TASK_ID, String.valueOf(task.id())));
        title.addClassName("view-title");
        PulseCenter taskCenter = new PulseCenter(taskList);
        taskCenter.setOpenHandler(openDetails);
        taskCenter.setViewAllHandler(() -> UI.getCurrent().navigate(TasksView.class));
        Div navbarEnd = new Div(new PulseNotificationButton(), taskCenter);
        navbarEnd.addClassName("navbar-end");
        addToNavbar(new DrawerToggle(), title, navbarEnd);
        PulseTray tray = new PulseTray(taskList);
        tray.setOpenHandler(openDetails);
        getElement().appendChild(tray.getElement());
        PulseWatcher.watch(this, taskList).setOpenHandler(openDetails);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        title.setText(titleOf(getContent()));
    }

    private static String titleOf(Component view) {
        if (view instanceof HasDynamicTitle dynamic) {
            return dynamic.getPageTitle();
        }
        PageTitle title = view.getClass().getAnnotation(PageTitle.class);
        return title != null ? title.value() : "";
    }
}
