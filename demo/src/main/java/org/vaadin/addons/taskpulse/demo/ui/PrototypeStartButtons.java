package org.vaadin.addons.taskpulse.demo.ui;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.signals.Signal;
import org.vaadin.addons.taskpulse.PulseAction;
import org.vaadin.addons.taskpulse.PulseTask;
import org.vaadin.addons.taskpulse.PulseTask.Outcome;
import org.vaadin.addons.taskpulse.PulseTask.State;
import org.vaadin.addons.taskpulse.PulseTaskContext;
import org.vaadin.addons.taskpulse.PulseTaskList;

/**
 * PROTOTYPE, throwaway: which look should an animated, signal-driven start
 * button have? Four variants of the Actions page's start button, switched with
 * {@code ?variant=A|B|C|D} and the floating bar at the bottom of the page.
 * <p>
 * Every variant follows the latest Task of its Action through the Task list's
 * signals, so it animates in every tab of the session, for a Retry from the tray
 * too, and needs no timers on the server: the "Done" and "Failed" states fade
 * back to the resting look in CSS ({@code prototype-start-buttons.css}).
 * <ul>
 * <li>A: today's regular button, as the baseline.</li>
 * <li>B: rolling label and animated icon, after Jouni's ActivationButton.</li>
 * <li>C: the button fills with the Task's Progress and shows its Status text.</li>
 * <li>D: the button collapses into a progress ring; clicking the ring cancels.</li>
 * </ul>
 */
final class PrototypeStartButtons {

    static final List<String> VARIANTS = List.of("A", "B", "C", "D");

    static String name(String variant) {
        return switch (variant) {
            case "B" -> "Rolling label";
            case "C" -> "Progress fill";
            case "D" -> "Ring morph";
            default -> "Current";
        };
    }

    private PrototypeStartButtons() {
    }

    /** The start button of the given variant, or {@code fallback} (variant A) for any other key. */
    static Component create(String variant, PulseTaskList taskList, PulseAction action,
            Consumer<PulseTaskContext> work, Function<PulseAction, Button> fallback) {
        return switch (variant) {
            case "B" -> rolling(taskList, action, work);
            case "C" -> fill(taskList, action, work);
            case "D" -> ring(taskList, action, work);
            default -> fallback.apply(action);
        };
    }

    // ------------------------------------------------------------ signals

    /** What a variant needs to know about the latest Task of its Action. */
    private record Follow(Signal<Optional<PulseTask>> latest, Signal<String> phase, Signal<Double> progress,
            Signal<Boolean> determinate) {
    }

    private static Follow follow(PulseTaskList taskList, PulseAction action) {
        Signal<Optional<PulseTask>> latest = Signal.computed(() -> {
            List<PulseTask> tasks = taskList.tasks().get();
            for (int i = tasks.size() - 1; i >= 0; i--) {
                if (tasks.get(i).action().equals(action)) {
                    return Optional.of(tasks.get(i));
                }
            }
            return Optional.empty();
        });
        // A Task that had already finished when the button was built rests: coming
        // back to the page later must not replay its "Done"
        long stale = Signal.untracked(() -> latest.get()
                .filter(task -> task.state().get() != State.RUNNING)
                .map(PulseTask::id)
                .orElse(-1L));
        Signal<String> phase = Signal.computed(() -> latest.get()
                .map(task -> {
                    State state = task.state().get();
                    if (state != State.RUNNING && task.id() == stale) {
                        return "idle";
                    }
                    return state.name().toLowerCase();
                })
                .orElse("idle"));
        Signal<Double> progress = Signal.computed(() -> latest.get().map(t -> t.progress().get()).orElse(0.0));
        Signal<Boolean> determinate = Signal.computed(
                () -> latest.get().map(t -> t.determinate().get()).orElse(false));
        return new Follow(latest, phase, progress, determinate);
    }

    /**
     * The button itself: a regular Button, kept enabled so that the animation isn't
     * greyed out, which ignores clicks while the Action runs (or cancels, for D).
     */
    private static Button base(String variant, PulseTaskList taskList, PulseAction action,
            Consumer<PulseTaskContext> work, Follow follow, boolean clickCancels) {
        Button button = new Button();
        button.addClassNames("pb", "pb-" + variant);
        button.getElement().bindAttribute("data-phase", follow.phase());
        button.addClickListener(e -> {
            if (!taskList.isRunning(action).peek()) {
                taskList.start(action, work);
            } else if (clickCancels) {
                follow.latest().peek().ifPresent(PulseTask::cancel);
            }
        });
        button.getElement().bindAttribute("aria-disabled", taskList.isRunning(action)
                .map(running -> running && !clickCancels ? "true" : "false"));
        button.getElement().bindAttribute("aria-label", follow.phase().map(phase -> switch (phase) {
            case "running" -> clickCancels ? "Cancel " + action.name() : action.name() + " running";
            case "succeeded" -> action.name() + " done";
            case "failed" -> action.name() + " failed";
            default -> "Start " + action.name();
        }));
        Signal.effect(button, () -> button.getStyle().set("--value", String.valueOf(follow.progress().get())));
        button.bindClassName("determinate", follow.determinate());
        return button;
    }

    // ------------------------------------------------------------ B: rolling label

    private static Component rolling(PulseTaskList taskList, PulseAction action, Consumer<PulseTaskContext> work) {
        Follow follow = follow(taskList, action);
        Button button = base("rolling", taskList, action, work, follow, false);

        Html icon = new Html("""
                <span class="pb-icon"><svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle class="pb-spin" cx="12" cy="12" r="9" pathLength="100"/>
                  <path class="pb-play" d="M10 8.5v7l5.5-3.5z"/>
                  <path class="pb-check" d="M7.5 12.5l3 3 6-6.5" pathLength="1"/>
                  <path class="pb-cross" d="M9 9l6 6M15 9l-6 6" pathLength="1"/>
                </svg></span>""");
        icon.getElement().setAttribute("slot", "prefix");
        button.getElement().appendChild(icon.getElement());

        Span reel = new Span(new Span("Start"), new Span("Running…"), new Span("Done"), new Span("Failed"));
        reel.addClassName("pb-reel");
        reel.getElement().setAttribute("aria-hidden", "true");
        button.getElement().appendChild(reel.getElement());
        return button;
    }

    // ------------------------------------------------------------ C: progress fill

    private static Component fill(PulseTaskList taskList, PulseAction action, Consumer<PulseTaskContext> work) {
        Follow follow = follow(taskList, action);
        Button button = base("fill", taskList, action, work, follow, false);

        Span bar = new Span();
        bar.addClassName("pb-bar");
        bar.getElement().setAttribute("slot", "suffix");
        bar.getElement().setAttribute("aria-hidden", "true");

        Span rest = new Span("Start");
        rest.addClassName("pb-rest");

        Span status = new Span();
        status.addClassName("pb-status");
        status.bindText(Signal.computed(() -> follow.latest().get().map(task -> switch (task.state().get()) {
            case RUNNING -> task.statusText().get().isEmpty() ? "Running…" : task.statusText().get();
            case SUCCEEDED -> task.outcome().get()
                    .map(o -> o instanceof Outcome.Succeeded s && !s.resultSummary().isEmpty()
                            ? "✓ " + s.resultSummary() : "✓ Done")
                    .orElse("✓ Done");
            case FAILED -> "✕ Failed";
            case CANCELLED -> "Cancelled";
        }).orElse("")));

        Span percent = new Span();
        percent.addClassName("pb-percent");
        percent.bindText(follow.progress().map(p -> Math.round(p * 100) + "%"));

        Span labels = new Span(rest, status, percent);
        labels.addClassName("pb-labels");
        labels.getElement().setAttribute("aria-hidden", "true");
        button.getElement().appendChild(labels.getElement(), bar.getElement());
        return button;
    }

    // ------------------------------------------------------------ D: ring morph

    private static Component ring(PulseTaskList taskList, PulseAction action, Consumer<PulseTaskContext> work) {
        Follow follow = follow(taskList, action);
        Button button = base("ring", taskList, action, work, follow, true);
        button.getElement().bindAttribute("title", follow.phase()
                .map(phase -> "running".equals(phase) ? "Click to cancel" : ""));

        Span ring = new Span();
        ring.addClassName("pb-ring");
        Span stop = new Span();
        stop.addClassName("pb-stop");
        Html check = new Html("""
                <svg class="pb-done-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path class="pb-check" d="M6 12.5l4 4 8-9" pathLength="1"/>
                  <path class="pb-cross" d="M8 8l8 8M16 8l-8 8" pathLength="1"/>
                </svg>""");
        Span label = new Span("Start");
        label.addClassName("pb-label");
        Span done = new Span();
        done.addClassName("pb-done-label");
        done.bindText(follow.phase().map(phase -> "failed".equals(phase) ? "Failed" : "Done"));

        Span face = new Span(ring, stop, check, label, done);
        face.addClassName("pb-face");
        face.getElement().setAttribute("aria-hidden", "true");
        button.getElement().appendChild(face.getElement());
        return button;
    }

    // ------------------------------------------------------------ switcher

    /**
     * The floating bar that switches variants: arrows, the variant's name, and the
     * ← and → keys outside text fields. It updates {@code ?variant=} so a variant
     * survives a reload and can be shared.
     */
    static Component switcher(String current) {
        int index = Math.max(0, VARIANTS.indexOf(current));
        String previous = VARIANTS.get((index + VARIANTS.size() - 1) % VARIANTS.size());
        String next = VARIANTS.get((index + 1) % VARIANTS.size());

        Button back = new Button("←", e -> go(previous));
        back.getElement().setAttribute("data-dir", "previous");
        back.setAriaLabel("Previous variant");
        Button forward = new Button("→", e -> go(next));
        forward.getElement().setAttribute("data-dir", "next");
        forward.setAriaLabel("Next variant");
        Span label = new Span("Prototype · " + VARIANTS.get(index) + " (" + name(VARIANTS.get(index)) + ")");

        Div bar = new Div(back, label, forward);
        bar.addClassName("prototype-switcher");
        bar.addAttachListener(e -> bar.getElement().executeJs("""
                if (window.__prototypeKeys) return;
                window.__prototypeKeys = true;
                document.addEventListener('keydown', (event) => {
                  const target = event.composedPath()[0];
                  if (target.closest && target.closest('input, textarea, [contenteditable]')) return;
                  const dir = event.key === 'ArrowLeft' ? 'previous' : event.key === 'ArrowRight' ? 'next' : null;
                  const button = dir && document.querySelector('.prototype-switcher [data-dir=' + dir + ']');
                  if (button) button.click();
                });"""));
        return bar;
    }

    private static void go(String variant) {
        UI.getCurrent().navigate(ActionsView.class, QueryParameters.of("variant", variant));
    }
}
