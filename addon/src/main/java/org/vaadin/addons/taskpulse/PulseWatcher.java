package org.vaadin.addons.taskpulse;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;

/**
 * What TaskPulse does for each browser tab of the session, for as long as its
 * owner, usually the app's main layout, is attached:
 * <ul>
 * <li>reports whether the tab is visible, so that a Task that finishes while no
 * tab of the session is visible is Unseen;</li>
 * <li>starts the page title with the Unseen count and the overall Progress, as
 * in {@code (2) 45% · Actions};</li>
 * <li>shows a native notification for each Task that becomes Unseen, if the
 * user allowed it with a {@link PulseNotificationButton};</li>
 * <li>shows a toast in each visible tab when a Task succeeds or fails.</li>
 * </ul>
 * Start it once from the main layout's constructor:
 *
 * <pre>
 * PulseWatcher.watch(this).setOpenHandler(task -&gt; UI.getCurrent().navigate(...));
 * </pre>
 */
public final class PulseWatcher {

    private final PulseTaskList taskList;
    private final ValueSignal<String> viewTitle = new ValueSignal<>("");
    private final ValueSignal<Optional<Consumer<PulseTask>>> openHandler = new ValueSignal<>(Optional.empty());
    private final Set<Long> notified = new HashSet<>();
    private PulseTaskList.Tab tab;
    private Registration navigation;
    private boolean effectsAdded;

    private PulseWatcher(Component owner, PulseTaskList taskList) {
        this.taskList = taskList;
        PulseToasts.showIn(owner, taskList, openHandler);
        owner.addAttachListener(e -> attach(owner, e.getUI()));
        owner.addDetachListener(e -> detach());
        owner.getUI().ifPresent(ui -> attach(owner, ui));
    }

    /** Watches the current session's Task list for the tab the owner lives in. */
    public static PulseWatcher watch(Component owner) {
        return watch(owner, PulseTaskList.getCurrent());
    }

    /** Watches the Task list for the tab the owner lives in. */
    public static PulseWatcher watch(Component owner, PulseTaskList taskList) {
        return new PulseWatcher(owner, taskList);
    }

    /**
     * What "Open" and "Details" on a toast do, for example navigating to a page
     * with the Task's details. Without a handler, toasts have no such button.
     */
    public PulseWatcher setOpenHandler(Consumer<PulseTask> handler) {
        openHandler.set(Optional.ofNullable(handler));
        return this;
    }

    private void attach(Component owner, UI ui) {
        if (tab != null) {
            return;
        }
        tab = taskList.openTab();
        navigation = ui.addAfterNavigationListener(this::afterNavigation);
        if (!ui.getInternals().getActiveRouterTargetsChain().isEmpty()) {
            viewTitle.set(titleOf(ui.getInternals().getActiveRouterTargetsChain().getFirst()));
        }
        if (effectsAdded) {
            return;
        }
        effectsAdded = true;
        // Effects owned by the owner pause while it is detached
        Signal.effect(owner, () -> {
            PageVisibility visibility = ui.getPage().pageVisibilitySignal().get();
            tab.setVisible(visibility != PageVisibility.HIDDEN);
        });
        Signal.effect(owner, () -> {
            int unseen = taskList.unseenCount().get();
            String pageTitle = (unseen > 0 ? "(" + unseen + ") " : "")
                    + taskList.overallProgress().get()
                            .map(progress -> (int) (progress * 100) + "% · ")
                            .orElse("")
                    + viewTitle.get();
            // After the router, which sets the plain view title once navigation ends
            ui.beforeClientResponse(owner, context -> ui.getPage().setTitle(pageTitle));
        });
        Signal.effect(owner, () -> {
            List<PulseTask> unseen = taskList.tasks().getValues()
                    .filter(task -> task.unseen().get())
                    .toList();
            Signal.untracked(() -> {
                unseen.stream()
                        .filter(task -> notified.add(task.id()))
                        .forEach(task -> notify(ui, task));
                notified.retainAll(unseen.stream().map(PulseTask::id).toList());
                return null;
            });
        });
    }

    private void detach() {
        if (tab != null) {
            tab.close();
            tab = null;
            navigation.remove();
        }
    }

    private void afterNavigation(AfterNavigationEvent event) {
        viewTitle.set(titleOf(event.getActiveChain().getFirst()));
    }

    /**
     * Shows a native notification if the user granted permission. Every hidden tab
     * asks; the browser shows one, because the Task id tags it.
     */
    private static void notify(UI ui, PulseTask task) {
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
