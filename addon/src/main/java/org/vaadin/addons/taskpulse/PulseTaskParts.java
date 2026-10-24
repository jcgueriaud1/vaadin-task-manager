package org.vaadin.addons.taskpulse;

import java.nio.file.Path;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.signals.Signal;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;
import org.vaadin.addons.taskpulse.PulseTask.State;

/**
 * The pieces the tray, the task center and the Blocking Task dialog build a
 * Task's row from, for an app's own views of Tasks, such as a page with their
 * details. Each follows its Task's signals, and is styled by the add-on's
 * stylesheet, which the tray loads.
 */
public final class PulseTaskParts {

    /** The add-on's stylesheet, which each of its components loads. */
    static final String STYLESHEET = "taskpulse/taskpulse.css";

    private PulseTaskParts() {
    }

    /**
     * What the Task is doing now, for example "50% · Step 2 of 4: aggregating…"
     * or "Running · Preparing recipients" while Running, and "Succeeded · 5 PDF
     * files" or "Failed · Server unreachable" once Finished.
     */
    public static Div meta(PulseTask task) {
        Signal<Boolean> showsPercent = task.determinate();
        Span state = new Span(task.state().map(PulseTaskParts::label));
        state.addClassName("pulse-state");
        state.bindVisible(Signal.not(showsPercent));
        Span percent = new Span(task.progress().map(p -> Math.round(p * 100) + "%"));
        percent.addClassName("pulse-percent");
        percent.bindVisible(showsPercent);
        Span statusText = new Span(task.statusText());
        statusText.addClassName("pulse-status-text");
        statusText.bindVisible(task.running());
        Div meta = new Div(state, percent, statusText, resultSummary(task), errorMessage(task));
        meta.addClassName("pulse-meta");
        return meta;
    }

    /** A ring while the Task is Running, then an icon for how it finished. */
    public static Div indicator(PulseTask task) {
        ProgressRing ring = new ProgressRing(task.progress(), task.determinate());
        ring.bindVisible(task.running());
        Div indicator = new Div(ring,
                stateIcon(task, State.SUCCEEDED, VaadinIcon.CHECK_CIRCLE),
                stateIcon(task, State.FAILED, VaadinIcon.EXCLAMATION_CIRCLE),
                stateIcon(task, State.CANCELLED, VaadinIcon.CLOSE_CIRCLE));
        indicator.addClassName("pulse-indicator");
        return indicator;
    }

    private static Component stateIcon(PulseTask task, State state, VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.addClassNames("pulse-state-icon", state.name().toLowerCase());
        icon.getElement().setAttribute("aria-hidden", "true");
        icon.bindVisible(task.state().map(s -> s == state));
        return icon;
    }

    /** Indeterminate until the Task's work reports Progress; shown while Running. */
    public static ProgressBar progressBar(PulseTask task) {
        ProgressBar bar = new ProgressBar();
        bar.addClassName("pulse-progress");
        bar.bindValue(task.progress());
        Signal.effect(bar, () -> bar.setIndeterminate(!task.determinate().get()));
        bar.bindVisible(task.running());
        return bar;
    }

    /** The Result summary of a Succeeded Task, shown only once it Succeeded. */
    public static Span resultSummary(PulseTask task) {
        Span span = new Span(task.outcome().map(
                o -> o.orElse(null) instanceof Outcome.Succeeded s ? s.resultSummary() : ""));
        span.addClassName("pulse-result-summary");
        span.bindVisible(task.outcome().map(o -> o.orElse(null) instanceof Outcome.Succeeded));
        return span;
    }

    /**
     * A link with the name of the Result file of a Succeeded Task, which opens
     * it in a new browser tab. Shown only once the Task Succeeded with one.
     */
    public static Anchor resultFile(PulseTask task) {
        Signal<Optional<Path>> file = task.outcome().map(
                o -> o.orElse(null) instanceof Outcome.Succeeded s ? s.resultFile() : Optional.empty());
        Anchor link = new Anchor();
        link.addClassName("pulse-result-file");
        link.setTarget(AnchorTarget.BLANK);
        Signal.effect(link, () -> file.get().ifPresent(path -> {
            String name = path.getFileName().toString();
            link.setHref(DownloadHandler.forFile(path.toFile()).inline(), AttachmentType.INLINE);
            link.removeAll();
            Icon icon = VaadinIcon.FILE_O.create();
            icon.getElement().setAttribute("aria-hidden", "true");
            link.add(icon, new Span(name));
            link.getElement().setAttribute("aria-label", "Open " + name);
        }));
        link.bindVisible(file.map(Optional::isPresent));
        return link;
    }

    /** The error of a Failed Task, shown only once it Failed. */
    public static Span errorMessage(PulseTask task) {
        Span span = new Span(task.outcome().map(
                o -> o.orElse(null) instanceof Outcome.Failed f ? f.errorMessage() : ""));
        span.addClassName("pulse-error-message");
        span.bindVisible(task.outcome().map(o -> o.orElse(null) instanceof Outcome.Failed));
        return span;
    }

    /** The Task state as the user reads it, for example "Succeeded". */
    public static String label(State state) {
        return switch (state) {
            case RUNNING -> "Running";
            case SUCCEEDED -> "Succeeded";
            case FAILED -> "Failed";
            case CANCELLED -> "Cancelled";
        };
    }
}
