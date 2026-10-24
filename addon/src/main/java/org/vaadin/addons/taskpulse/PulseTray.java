package org.vaadin.addons.taskpulse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;

/**
 * The floating tray in the bottom-right that lists the session's Tasks. Every
 * {@link PulseTab} shows one; turn it off with {@link PulseTab#setTrayEnabled(boolean)}.
 */
@StyleSheet(PulseTaskParts.STYLESHEET)
public class PulseTray extends Div {

    private final Signal<Optional<Consumer<PulseTask>>> openHandler;

    PulseTray(PulseTab pulseTab) {
        PulseTaskList taskList = pulseTab.taskList();
        this.openHandler = pulseTab.openHandler();
        addClassName("pulse-tray");
        getElement().setAttribute("role", "region");
        getElement().setAttribute("aria-label", "Background tasks");
        Span headline = new Span();
        headline.addClassName("pulse-tray-headline");
        headline.bindText(Signal.computed(() -> headline(taskList.trayTasks().get())));
        // Collapsing is a per-tab choice, so it isn't kept in the Task list
        ValueSignal<Boolean> collapsed = new ValueSignal<>(false);
        Button toggle = new Button(new Icon(VaadinIcon.CHEVRON_DOWN_SMALL));
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        toggle.addClassName("pulse-tray-toggle");
        toggle.getElement().bindAttribute("aria-label", collapsed.map(c -> c ? "Expand tray" : "Collapse tray"));
        toggle.getElement().bindAttribute("aria-expanded", collapsed.map(c -> String.valueOf(!c)));
        toggle.bindClassName("collapsed", collapsed);
        toggle.addClickListener(e -> collapsed.update(c -> !c));
        Div header = new Div(headline, toggle);
        header.addClassName("pulse-tray-header");

        Div rows = new Div();
        rows.addClassName("pulse-tray-rows");
        Signal.effect(rows, () -> {
            List<PulseTask> shown = taskList.trayTasks().get();
            rows.removeAll();
            Signal.untracked(() -> {
                shown.forEach(task -> rows.add(row(task, taskList)));
                return null;
            });
        });
        rows.bindVisible(Signal.not(collapsed));
        add(header, overallProgress(taskList.overallProgress()), rows);
        bindVisible(taskList.trayTasks().map(tasks -> !tasks.isEmpty()));
    }

    /** The average Progress of Running Tasks that report Progress, hidden when there are none. */
    private static ProgressBar overallProgress(Signal<Optional<Double>> overall) {
        ProgressBar bar = new ProgressBar();
        bar.addClassName("pulse-tray-progress");
        bar.bindValue(overall.map(progress -> progress.orElse(0.0)));
        bar.bindVisible(overall.map(Optional::isPresent));
        return bar;
    }

    /** For example "1 task running · 2 tasks finished", or "" when there's nothing. */
    static String headline(List<PulseTask> tasks) {
        long running = tasks.stream().filter(task -> task.running().get()).count();
        long finished = tasks.size() - running;
        List<String> parts = new ArrayList<>();
        if (running > 0) {
            parts.add(count(running) + " running");
        }
        if (finished > 0) {
            parts.add(count(finished) + " finished");
        }
        return String.join(" · ", parts);
    }

    private static String count(long tasks) {
        return tasks == 1 ? "1 task" : tasks + " tasks";
    }

    private Div row(PulseTask task, PulseTaskList taskList) {
        Span name = new Span(task.action().name());
        name.addClassName("pulse-name");
        Div body = new Div(name, PulseTaskParts.meta(task), PulseTaskParts.resultFile(task));
        body.addClassName("pulse-row-body");

        Button cancel = new Button("Cancel", e -> task.cancel());
        cancel.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        cancel.setAriaLabel("Cancel " + task.action().name());
        cancel.addClassName("pulse-cancel");
        cancel.bindVisible(task.running());
        Button open = openButton(task, openHandler);
        open.bindVisible(Signal.computed(() -> openHandler.get().isPresent() && !task.running().get()));
        Button dismiss = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> task.dismiss());
        dismiss.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        dismiss.setAriaLabel("Dismiss " + task.action().name());
        dismiss.addClassName("pulse-dismiss");
        dismiss.bindVisible(Signal.not(task.running()));
        Div actions = new Div(cancel, open, dismiss);
        actions.addClassName("pulse-row-actions");

        Div row = new Div(PulseTaskParts.indicator(task), body, actions);
        row.addClassName("pulse-row");
        return row;
    }

    /** "Open", which hands the Task to the app's open handler. */
    static Button openButton(PulseTask task, Signal<Optional<Consumer<PulseTask>>> openHandler) {
        Button open = new Button("Open", e -> openHandler.peek().ifPresent(handler -> handler.accept(task)));
        open.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        open.addClassName("pulse-open");
        open.setAriaLabel("Open " + task.action().name());
        return open;
    }
}
