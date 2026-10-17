package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;

class TaskTimingTest {

    private static final Instant START = Instant.parse("2026-10-15T09:00:00Z");

    private final TestExecutor executor = new TestExecutor();
    private final TestClock clock = new TestClock(START);
    private final PulseTaskList taskList = new PulseTaskList(clock);
    private final ValueSignal<Instant> now = new ValueSignal<>(START);
    private final PulseAction report = new PulseAction("Generate report", BlockingMode.NON_BLOCKING);

    @Test
    void aRunningTaskHasRunSinceItStartedAndAFinishedOneKeepsHowLongItTook() {
        PulseTask task = taskList.start(report, executor, ctx -> {
            executor.pause();
            clock.advance(Duration.ofSeconds(30));
        });
        Signal<Duration> elapsed = task.elapsed(now);
        executor.runNext();
        tick(Duration.ofSeconds(12));

        assertEquals(START, task.startedAt());
        assertEquals(Duration.ofSeconds(12), elapsed.peek());

        executor.resume();
        tick(Duration.ofMinutes(5));

        assertEquals(Duration.ofSeconds(42), elapsed.peek());
    }

    @Test
    void timeLeftIsAStraightLineEstimateFromProgressShownOnlyAboveFivePercent() {
        PulseTask task = taskList.start(report, executor, ctx -> {
            clock.advance(Duration.ofSeconds(2));
            ctx.setProgress(0.05);
            executor.pause();
            clock.advance(Duration.ofSeconds(8));
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(1);
        });
        Signal<Optional<Duration>> timeLeft = task.timeLeft(now);
        assertEquals(Optional.empty(), timeLeft.peek());

        executor.runNext();
        now.set(clock.instant());
        assertEquals(Optional.empty(), timeLeft.peek(), "5% is not above 5%");

        executor.resume();
        now.set(clock.instant());
        assertEquals(Optional.of(Duration.ofSeconds(30)), timeLeft.peek(), "a quarter done in 10 s leaves 30 s");

        tick(Duration.ofSeconds(10));
        assertEquals(Optional.of(Duration.ofSeconds(60)), timeLeft.peek(),
                "the estimate follows the clock: a quarter done in 20 s leaves 60 s");

        executor.resume();
        assertEquals(Optional.empty(), timeLeft.peek(), "a Finished Task has none, without a tick");
    }

    @Test
    void aTaskWithoutProgressHasNoTimeLeft() {
        PulseTask task = taskList.start(report, executor, ctx -> {
            ctx.setStatusText("Sending emails");
            executor.pause();
        });
        executor.runNext();
        tick(Duration.ofSeconds(10));

        assertEquals(Optional.empty(), task.timeLeft(now).peek());
    }

    /** Moves the clock and the ticking "now" a view would follow. */
    private void tick(Duration duration) {
        clock.advance(duration);
        now.set(clock.instant());
    }

}
