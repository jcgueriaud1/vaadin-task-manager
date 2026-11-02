package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;

/** A start button follows the latest Task of its Action, whoever started it. */
@ViewPackages(classes = StartButtonTest.EmptyView.class)
class StartButtonTest extends BrowserlessTest {

    private static final PulseAction EXPORT = new PulseAction("Export data", BlockingMode.NON_BLOCKING);

    @Route("empty")
    public static class EmptyView extends Div {
    }

    private final TestExecutor executor = new TestExecutor();
    private PulseTaskList taskList;

    @BeforeEach
    void installTaskList() {
        navigate(EmptyView.class);
        taskList = PulseTaskList.install(VaadinSession.getCurrent(),
                new PulseTaskList(new TestClock(Instant.parse("2026-01-01T10:00:00Z")), executor));
    }

    @AfterEach
    void finishLeftoverWork() {
        executor.runAll();
    }

    @Test
    void aClickStartsATaskOfTheActionAndTheButtonSaysItIsRunning() {
        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
        }));
        assertEquals("idle", phase(button));
        assertEquals("Start Export data", name(button));

        click(button);

        assertEquals(1, executor.queued());
        assertEquals("running", phase(button));
        assertEquals("Export data running", name(button));
    }

    @Test
    void aClickWhileTheActionRunsStartsNothing() {
        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
        }));
        click(button);
        click(button);

        assertEquals(1, executor.queued());
        assertEquals(1, taskList.tasks().peek().size());
    }

    @Test
    void itShowsATaskStartedElsewhereSuchAsAnotherTabOrARetry() {
        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
        }));
        taskList.start(EXPORT, executor, ctx -> {
        });
        runPendingSignalsTasks();

        assertEquals("running", phase(button));
    }

    @Test
    void itSaysHowTheTaskEndedAndKeepsItsRestingNameForTheBrowserToGoBackTo() {
        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
        }));
        click(button);
        executor.runAll();
        runPendingSignalsTasks();

        assertEquals("succeeded", phase(button));
        assertEquals("Export data done", name(button));
        assertEquals("Start Export data", button.getElement().getAttribute("data-resting-name"));

        PulseStartButton failing = attached(new PulseStartButton(new PulseAction("Sync", BlockingMode.NON_BLOCKING),
                ctx -> {
                    throw new IllegalStateException("Server unreachable");
                }));
        click(failing);
        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("failed", phase(failing));
        assertEquals("Sync failed", name(failing));
    }

    @Test
    void aCancelledTaskLeavesTheButtonResting() {
        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
        }));
        click(button);
        taskList.tasks().peek().getFirst().cancel();
        executor.runAll();
        runPendingSignalsTasks();

        assertEquals("cancelled", phase(button));
        assertEquals("Start Export data", name(button));
    }

    @Test
    void aTaskThatFinishedBeforeTheButtonAttachedIsNotShownAgain() {
        taskList.start(EXPORT, executor, ctx -> {
        });
        executor.runAll();

        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
        }));
        assertEquals("idle", phase(button));

        click(button);
        executor.runAll();
        runPendingSignalsTasks();
        assertEquals("succeeded", phase(button), "a Task it saw running");

        UI.getCurrent().remove(button);
        UI.getCurrent().add(button);
        runPendingSignalsTasks();
        assertEquals("idle", phase(button), "attached again later");
    }

    @Test
    void itFollowsTheProgressOnceTheWorkReportsIt() {
        PulseStartButton button = attached(new PulseStartButton(EXPORT, ctx -> {
            ctx.setProgress(0.25);
            executor.pause();
        }));
        click(button);
        runPendingSignalsTasks();
        assertFalse(button.getElement().getClassList().contains("determinate"));

        executor.runNext();
        runPendingSignalsTasks();
        assertTrue(button.getElement().getClassList().contains("determinate"));
        assertEquals("0.25", button.getElement().getStyle().get("--value"));
    }

    @Test
    void itsNameHoldsItsLabelAndTheActionWithoutRepeatingIt() {
        assertEquals("Start Export data", PulseStartButton.restingName("Start", EXPORT));
        assertEquals("Export data", PulseStartButton.restingName("Export", EXPORT));
        assertEquals("Run Export data now", PulseStartButton.restingName("Run Export data now", EXPORT));
    }

    private static PulseStartButton attached(PulseStartButton button) {
        UI.getCurrent().add(button);
        return button;
    }

    private void click(PulseStartButton button) {
        test((Button) button.getChildren().findFirst().orElseThrow()).click();
        runPendingSignalsTasks();
    }

    private static String phase(PulseStartButton button) {
        return button.getElement().getAttribute("data-phase");
    }

    private static String name(PulseStartButton button) {
        return button.getElement().getAttribute("aria-label");
    }
}
