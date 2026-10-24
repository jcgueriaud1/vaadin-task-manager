package org.vaadin.addons.taskpulse;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/**
 * The navbar button that says how many Tasks are Running, with a ring for their
 * overall Progress. It opens a popover with the Running and recent Finished
 * Tasks, and the button that turns on native notifications. "Open" and "View
 * all" do what the tab's {@link PulseTab} says.
 */
@StyleSheet(PulseTaskParts.STYLESHEET)
public class PulseCenter extends Div {

    static final int RECENT_FINISHED = 5;

    // The tab this task center is attached to, whose handlers "Open" and "View all" use
    private final ValueSignal<Optional<PulseTab>> pulseTab = new ValueSignal<>(Optional.empty());
    private final Signal<Optional<Consumer<PulseTask>>> openHandler =
            Signal.computed(() -> pulseTab.get().flatMap(tab -> tab.openHandler().get()));
    private final Popover popover = new Popover();

    /**
     * A task center of the session's Task list. When it attaches, it sets up the
     * {@link PulseTab} of its browser tab if there is none yet.
     */
    public PulseCenter() {
        PulseTaskList taskList = PulseTaskList.getCurrent();
        Signal<Optional<Runnable>> viewAllHandler =
                Signal.computed(() -> pulseTab.get().flatMap(tab -> tab.viewAllHandler().get()));
        addAttachListener(e -> pulseTab.set(Optional.of(PulseTab.of(e.getUI()))));
        addClassName("pulse-center");
        Signal<Integer> runningCount = taskList.runningCount();
        Button button = new Button(runningCount.map(PulseCenter::buttonText));
        button.setAriaLabel("Task center");
        button.addClassName("pulse-center-button");
        button.bindClassName("running", runningCount.map(count -> count > 0));
        button.setPrefixComponent(buttonIcon(taskList));
        Span unseen = new Span(taskList.unseenCount().map(String::valueOf));
        unseen.addClassName("pulse-unseen-badge");
        unseen.getElement().setAttribute("aria-label", "Unseen Tasks");
        unseen.bindVisible(taskList.unseenCount().map(count -> count > 0));
        add(button, unseen);

        Div rows = new Div();
        rows.addClassName("pulse-center-rows");
        Signal.effect(rows, () -> {
            List<PulseTask> shown = shown(taskList);
            rows.removeAll();
            Signal.untracked(() -> {
                shown.forEach(task -> rows.add(row(task)));
                return null;
            });
        });
        Span empty = new Span("No background tasks yet.");
        empty.addClassName("pulse-center-empty");
        empty.bindVisible(Signal.computed(() -> taskList.tasks().get().stream().findAny().isEmpty()));
        Button viewAll = new Button("View all", e -> {
            viewAllHandler.peek().ifPresent(Runnable::run);
            popover.close();
        });
        viewAll.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        viewAll.addClassName("pulse-center-view-all");
        viewAll.bindVisible(viewAllHandler.map(Optional::isPresent));
        Div header = new Div(new H3("Background tasks"), viewAll);
        header.addClassName("pulse-center-header");
        PulseNotificationButton notifications = new PulseNotificationButton();
        notifications.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        Div footer = new Div(notifications);
        footer.addClassName("pulse-center-footer");

        popover.add(header, rows, empty, footer);
        popover.addClassName("pulse-center-popover");
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.setTarget(button);
    }

    /** For example "2 running", or "Tasks" when nothing is Running. */
    static String buttonText(int running) {
        return running > 0 ? running + " running" : "Tasks";
    }

    /** A ring for the overall Progress, a spinning one if no Running Task reports it, a list icon when idle. */
    private static Div buttonIcon(PulseTaskList taskList) {
        Signal<Optional<Double>> overall = taskList.overallProgress();
        Signal<Boolean> running = taskList.runningCount().map(count -> count > 0);
        ProgressRing progress = new ProgressRing(overall.map(p -> p.orElse(0.0)), overall.map(Optional::isPresent));
        progress.bindVisible(running);
        Icon idle = VaadinIcon.TASKS.create();
        idle.bindVisible(Signal.not(running));
        Div icon = new Div(progress, idle);
        icon.addClassName("pulse-center-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        return icon;
    }

    /** Running Tasks, then the most recent Finished ones, newest first. */
    private static List<PulseTask> shown(PulseTaskList taskList) {
        List<PulseTask> newestFirst = taskList.tasks().get().stream().toList().reversed();
        Stream<PulseTask> running = newestFirst.stream()
                .filter(task -> task.running().get());
        Stream<PulseTask> finished = newestFirst.stream()
                .filter(task -> !task.running().get())
                .limit(RECENT_FINISHED);
        return Stream.concat(running, finished).toList();
    }

    private Div row(PulseTask task) {
        Span name = new Span(task.action().name());
        name.addClassName("pulse-name");
        Div body = new Div(name, PulseTaskParts.meta(task), PulseTaskParts.resultFile(task));
        body.addClassName("pulse-row-body");
        Button open = PulseTray.openButton(task, openHandler);
        open.addClickListener(e -> popover.close());
        open.bindVisible(openHandler.map(Optional::isPresent));
        Div row = new Div(PulseTaskParts.indicator(task), body, open);
        row.addClassName("pulse-center-row");
        return row;
    }
}
