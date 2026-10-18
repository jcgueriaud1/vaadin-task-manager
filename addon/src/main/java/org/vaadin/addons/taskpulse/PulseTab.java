package org.vaadin.addons.taskpulse;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;

/**
 * What TaskPulse does in one browser tab of the session. There is one per
 * {@link UI}, created the first time a TaskPulse component attaches to it or
 * the app asks for it with {@link #current()}. It
 * <ul>
 * <li>tracks whether the tab is visible, so that a Task that finishes while no
 * tab of the session is visible is Unseen;</li>
 * <li>shows the {@link PulseTray} in the bottom-right;</li>
 * <li>starts the page title with the Unseen count and the overall Progress, as
 * in {@code (2) 45% · Orders};</li>
 * <li>shows a toast when a Task succeeds or fails while the tab is visible, and
 * a native notification for each Task that becomes Unseen, once the user
 * allowed them;</li>
 * <li>holds what "Open" and "View all" do, for every one of those surfaces.</li>
 * </ul>
 * An app sets its handlers once, for example from its main layout:
 *
 * <pre>
 * PulseTab.current()
 *         .setOpenHandler(task -&gt; UI.getCurrent().navigate(...))
 *         .setViewAllHandler(() -&gt; UI.getCurrent().navigate(...));
 * </pre>
 */
public final class PulseTab {

    /** The event a click on a native notification sends back, with the Task's id. */
    private static final String NOTIFICATION_CLICK = "taskpulse-notification-click";

    private final UI ui;
    private final PulseTaskList taskList;
    private final PulseTaskList.Tab tab;
    private final PulseTray tray;
    private final ValueSignal<Optional<Consumer<PulseTask>>> openHandler = new ValueSignal<>(Optional.empty());
    private final ValueSignal<Optional<Runnable>> viewAllHandler = new ValueSignal<>(Optional.empty());
    private final ValueSignal<Boolean> pageTitleUpdates = new ValueSignal<>(true);
    private final ValueSignal<String> viewTitle = new ValueSignal<>("");
    private final Set<Long> notified = new HashSet<>();

    private PulseTab(UI ui) {
        this.ui = ui;
        this.taskList = PulseTaskList.get(ui.getSession());
        this.tab = taskList.openTab();
        ui.addDetachListener(e -> tab.close());
        ui.addAfterNavigationListener(this::afterNavigation);
        if (!ui.getInternals().getActiveRouterTargetsChain().isEmpty()) {
            viewTitle.set(titleOf(ui.getInternals().getActiveRouterTargetsChain().getFirst()));
        }
        ui.getElement().addEventListener(NOTIFICATION_CLICK, e -> taskList
                .find(e.getEventData().get("event.detail").asLong())
                .ifPresent(this::open))
                .addEventData("event.detail");
        tray = new PulseTray(this);
        ui.add(tray);
        // Effects owned by the UI last as long as the tab
        Signal.effect(ui, () -> {
            PageVisibility visibility = ui.getPage().pageVisibilitySignal().get();
            tab.setVisible(visibility != PageVisibility.HIDDEN);
        });
        Signal.effect(ui, this::updatePageTitle);
        Signal.effect(ui, this::notifyUnseen);
        PulseToasts.showIn(ui, taskList, openHandler);
    }

    /**
     * The TaskPulse of the current browser tab, created on first use.
     *
     * @throws IllegalStateException if there is no current UI
     */
    public static PulseTab current() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            throw new IllegalStateException("There is no current UI");
        }
        return of(ui);
    }

    /** The TaskPulse of the browser tab of the UI, created on first use. */
    public static PulseTab of(UI ui) {
        PulseTab pulseTab = ComponentUtil.getData(ui, PulseTab.class);
        if (pulseTab == null) {
            pulseTab = new PulseTab(ui);
            ComponentUtil.setData(ui, PulseTab.class, pulseTab);
        }
        return pulseTab;
    }

    /** The session's Task list, which this tab shows. */
    public PulseTaskList taskList() {
        return taskList;
    }

    /**
     * What "Open" and "Details" do, in the tray, the task center and the toasts,
     * and what a click on a native notification does: for example navigating to
     * a page with the Task's details. Without a handler, those buttons are hidden.
     */
    public PulseTab setOpenHandler(Consumer<PulseTask> handler) {
        openHandler.set(Optional.ofNullable(handler));
        return this;
    }

    /**
     * What "View all" in the task center does, for example navigating to a page
     * with all Tasks. Without a handler, the button is hidden.
     */
    public PulseTab setViewAllHandler(Runnable handler) {
        viewAllHandler.set(Optional.ofNullable(handler));
        return this;
    }

    /** Whether the tray shows in this tab. It does by default. */
    public PulseTab setTrayEnabled(boolean enabled) {
        if (enabled && tray.getParent().isEmpty()) {
            ui.add(tray);
        } else if (!enabled) {
            tray.removeFromParent();
        }
        return this;
    }

    /**
     * Whether the page title starts with the Unseen count and the overall
     * Progress. It does by default; turn it off when the app sets the title
     * itself.
     */
    public PulseTab setPageTitleUpdates(boolean enabled) {
        pageTitleUpdates.set(enabled);
        return this;
    }

    Signal<Optional<Consumer<PulseTask>>> openHandler() {
        return openHandler.asReadonly();
    }

    Signal<Optional<Runnable>> viewAllHandler() {
        return viewAllHandler.asReadonly();
    }

    private void open(PulseTask task) {
        openHandler.peek().ifPresent(handler -> handler.accept(task));
    }

    private void afterNavigation(AfterNavigationEvent event) {
        viewTitle.set(titleOf(event.getActiveChain().getFirst()));
    }

    private void updatePageTitle() {
        if (!pageTitleUpdates.get()) {
            return;
        }
        int unseen = taskList.unseenCount().get();
        String pageTitle = (unseen > 0 ? "(" + unseen + ") " : "")
                + taskList.overallProgress().get()
                        .map(progress -> (int) (progress * 100) + "% · ")
                        .orElse("")
                + viewTitle.get();
        // After the router, which sets the plain view title once navigation ends
        ui.beforeClientResponse(ui, context -> ui.getPage().setTitle(pageTitle));
    }

    private void notifyUnseen() {
        List<PulseTask> unseen = taskList.tasks().get().stream()
                .filter(task -> task.unseen().get())
                .toList();
        Signal.untracked(() -> {
            unseen.stream()
                    .filter(task -> notified.add(task.id()))
                    .forEach(this::notify);
            notified.retainAll(unseen.stream().map(PulseTask::id).toList());
            return null;
        });
    }

    /**
     * Shows a native notification if the user granted permission. Every hidden tab
     * asks; the browser shows one, because the Task id tags it. A click opens the
     * Task in the tab that showed it.
     */
    private void notify(PulseTask task) {
        ui.getPage().executeJs("""
                if (!('Notification' in window) || Notification.permission !== 'granted') {
                    return;
                }
                const notification = new Notification($0, { body: $1, tag: String($2) });
                notification.onclick = () => {
                    window.focus();
                    notification.close();
                    $3.dispatchEvent(new CustomEvent($4, { detail: $2 }));
                };
                """, task.action().name(), body(task), task.id(), ui.getElement(), NOTIFICATION_CLICK);
    }

    /** The Result summary of a Succeeded Task, the error of a Failed one. */
    private static String body(PulseTask task) {
        Outcome outcome = task.outcome().peek()
                .orElseThrow(() -> new IllegalStateException("A Running Task is never Unseen"));
        return switch (outcome) {
            case Outcome.Succeeded succeeded -> succeeded.resultSummary().isEmpty() ? "Succeeded" : succeeded.resultSummary();
            case Outcome.Failed failed -> failed.errorMessage();
            case Outcome.Cancelled cancelled -> "Cancelled";
        };
    }

    /** The view's title, as the router would set it: dynamic, from {@code @PageTitle}, or none. */
    private static String titleOf(Object view) {
        if (view instanceof HasDynamicTitle dynamic) {
            return dynamic.getPageTitle();
        }
        PageTitle title = view.getClass().getAnnotation(PageTitle.class);
        return title != null ? title.value() : "";
    }
}
