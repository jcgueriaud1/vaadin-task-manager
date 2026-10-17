package org.vaadin.addons.taskpulse.demo.ui;

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
import org.vaadin.addons.taskpulse.PulseTab;
import org.vaadin.addons.taskpulse.PulseTaskList;

/**
 * The app shell: a drawer with the app's pages and a navbar with the view title
 * and the task center. The task center sets up the tab's {@link PulseTab}, which
 * adds the tray, the toasts, native notifications and the Progress in the page
 * title. Every "Open" leads to the Task on the Background tasks page.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final H1 title = new H1();

    /** Takes the session's Task list so that the app's bean, with its clock, is the one installed. */
    public MainLayout(PulseTaskList taskList) {
        Span appName = new Span("TaskPulse");
        appName.addClassName("app-name");
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Actions", ActionsView.class, VaadinIcon.PLAY_CIRCLE_O.create()),
                new SideNavItem("Background tasks", TasksView.class, VaadinIcon.TASKS.create()));
        addToDrawer(appName, nav);

        title.addClassName("view-title");
        Div navbarEnd = new Div(new PulseCenter());
        navbarEnd.addClassName("navbar-end");
        addToNavbar(new DrawerToggle(), title, navbarEnd);
        PulseTab.current()
                .setOpenHandler(task -> UI.getCurrent().navigate(TasksView.class,
                        new RouteParameters(TasksView.TASK_ID, String.valueOf(task.id()))))
                .setViewAllHandler(() -> UI.getCurrent().navigate(TasksView.class));
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
