package org.vaadin.addons.taskpulse;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.vaadin.addons.taskpulse.PulseTask.State;

/**
 * A button that starts a Task of an Action and shows how its latest Task is
 * doing: "Running…" with a spinner, or a ring once the work reports Progress,
 * then "Done" or "Failed" for a moment before it reads as a start button again.
 * <p>
 * It follows the Task list's signals rather than its own clicks, so it shows
 * the Action's Task in every browser tab of the session, also one started by a
 * Retry or by another tab. While the Action has a Running Task a click does
 * nothing: the Action has at most one Running Task per session. A Task that
 * had already finished when the button attached isn't shown again.
 * <p>
 * Its accessible name says what it does or what the Task is doing, for example
 * "Start Import data", "Import data running" or "Import data done". The Task's
 * end is announced by the toasts of {@link PulseTab}.
 * {@snippet :
 * add(new PulseStartButton(IMPORT_DATA, Imports::importData));
 * add(new PulseStartButton("Export customers", EXPORT_CUSTOMERS, Exports::exportCustomers));
 * }
 */
@StyleSheet(PulseTaskParts.STYLESHEET)
public class PulseStartButton extends Composite<Button>
        implements HasSize, HasStyle, Focusable<PulseStartButton> {

    static final String RUNNING_TEXT = "Running…";
    static final String DONE_TEXT = "Done";
    static final String FAILED_TEXT = "Failed";

    // The latest Task of the Action that had already finished when the button
    // attached: it rests instead of showing "Done" again
    private final ValueSignal<Long> finishedBeforeAttach = new ValueSignal<>(-1L);

    /** A start button labelled "Start", for the session's Task list. */
    public PulseStartButton(PulseAction action, Consumer<? super PulseTaskContext> work) {
        this("Start", action, work);
    }

    /**
     * A start button with its own label, such as "Export", for the session's
     * Task list. The Task's work runs on the Task list's executor.
     */
    public PulseStartButton(String text, PulseAction action, Consumer<? super PulseTaskContext> work) {
        PulseTaskList taskList = PulseTaskList.getCurrent();
        Button button = getContent();
        button.addClassName("pulse-start-button");

        Signal<Optional<PulseTask>> latest = Signal.computed(() -> latest(taskList.tasks().get(), action));
        Signal<String> phase = Signal.computed(() -> latest.get()
                .map(task -> phase(task, finishedBeforeAttach.get()))
                .orElse("idle"));
        rememberFinished(latest);
        addAttachListener(e -> rememberFinished(latest));

        button.addClickListener(e -> {
            if (!taskList.isRunning(action).peek()) {
                taskList.start(action, work);
            }
        });

        String restingName = restingName(text, action);
        button.getElement().setAttribute("data-resting-name", restingName);
        button.getElement().bindAttribute("data-phase", phase);
        button.getElement().bindAttribute("aria-label", phase.map(p -> switch (p) {
            case "running" -> action.name() + " running";
            case "succeeded" -> action.name() + " done";
            case "failed" -> action.name() + " failed";
            default -> restingName;
        }));
        Signal<Boolean> determinate = Signal.computed(() -> latest.get().map(t -> t.determinate().get()).orElse(false));
        button.bindClassName("determinate", determinate);
        Signal.effect(button, () -> button.getStyle().set("--value",
                String.valueOf(latest.get().map(t -> t.progress().get()).orElse(0.0))));

        Html icon = new Html("""
                <span class="pulse-start-icon" aria-hidden="true"><svg viewBox="0 0 24 24">
                  <circle class="pulse-start-spinner" cx="12" cy="12" r="9" pathLength="100"/>
                  <path class="pulse-start-play" d="M10 8.5v7l5.5-3.5z"/>
                  <path class="pulse-start-check" d="M7.5 12.5l3 3 6-6.5" pathLength="1"/>
                  <path class="pulse-start-cross" d="M9 9l6 6M15 9l-6 6" pathLength="1"/>
                </svg></span>""");
        button.setPrefixComponent(icon);

        // The visible labels, top to bottom; the button rolls up through them
        Span reel = new Span(new Span(text), new Span(RUNNING_TEXT), new Span(DONE_TEXT), new Span(FAILED_TEXT),
                new Span(text));
        reel.addClassName("pulse-start-reel");
        reel.getElement().setAttribute("aria-hidden", "true");
        button.getElement().appendChild(reel.getElement());

        // In the browser: aria-disabled follows the phase, also after the button
        // upgrades (which clears it), and the name returns to the resting one when
        // the "Done" or "Failed" look has faded out
        button.addAttachListener(e -> button.getElement().executeJs("""
                if (this.__pulseStart) return;
                this.__pulseStart = true;
                const sync = () => {
                  const running = this.getAttribute('data-phase') === 'running';
                  if (running && this.getAttribute('aria-disabled') !== 'true') {
                    this.setAttribute('aria-disabled', 'true');
                  } else if (!running && this.hasAttribute('aria-disabled')) {
                    this.removeAttribute('aria-disabled');
                  }
                };
                new MutationObserver(sync).observe(this, { attributes: true, attributeFilter: ['data-phase', 'aria-disabled'] });
                customElements.whenDefined('vaadin-button').then(() => requestAnimationFrame(sync));
                this.addEventListener('animationend', (event) => {
                  if (event.animationName === 'pulse-start-hold' && event.target === this) {
                    this.setAttribute('aria-label', this.getAttribute('data-resting-name'));
                  }
                });"""));
    }

    private void rememberFinished(Signal<Optional<PulseTask>> latest) {
        finishedBeforeAttach.set(Signal.untracked(() -> latest.get()
                .filter(task -> task.state().get() != State.RUNNING)
                .map(PulseTask::id)
                .orElse(-1L)));
    }

    private static Optional<PulseTask> latest(List<PulseTask> tasks, PulseAction action) {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            if (tasks.get(i).action().equals(action)) {
                return Optional.of(tasks.get(i));
            }
        }
        return Optional.empty();
    }

    private static String phase(PulseTask task, long finishedBeforeAttach) {
        State state = task.state().get();
        if (state != State.RUNNING && task.id() == finishedBeforeAttach) {
            return "idle";
        }
        return state.name().toLowerCase(Locale.ROOT);
    }

    // "Start Import data". A label that names the Action, or is part of its name
    // ("Export" for "Export data"), isn't repeated: the name holds the label either way
    static String restingName(String text, PulseAction action) {
        String label = text.toLowerCase(Locale.ROOT);
        String name = action.name().toLowerCase(Locale.ROOT);
        if (label.contains(name)) {
            return text;
        }
        return name.contains(label) ? action.name() : text + " " + action.name();
    }
}
