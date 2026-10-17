package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

class TaskTimingTest {

    private static final Instant START = Instant.parse("2026-10-15T09:00:00Z");

    private final TestExecutor executor = new TestExecutor();
    private final TestClock clock = new TestClock(START);
    private final TaskList taskList = new TaskList(clock);

    @Test
    void aRunningTaskHasRunSinceItStartedAndAFinishedOneKeepsHowLongItTook() {
        Task task = start(ctx -> {
            executor.pause();
            clock.advance(Duration.ofSeconds(30));
        });
        executor.runNext();
        clock.advance(Duration.ofSeconds(12));

        assertEquals(START, task.startedAt());
        assertEquals(Duration.ofSeconds(12), task.elapsed(clock.instant()));

        executor.resume();
        clock.advance(Duration.ofMinutes(5));

        assertEquals(Duration.ofSeconds(42), task.elapsed(clock.instant()));
    }

    @Test
    void timeLeftIsAStraightLineEstimateFromProgressShownOnlyAboveFivePercent() {
        Task task = start(ctx -> {
            clock.advance(Duration.ofSeconds(2));
            ctx.setProgress(0.05);
            executor.pause();
            clock.advance(Duration.ofSeconds(8));
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(1);
        });
        assertEquals(Optional.empty(), task.timeLeft(clock.instant()));

        executor.runNext();
        assertEquals(Optional.empty(), task.timeLeft(clock.instant()), "5% is not above 5%");

        executor.resume();
        assertEquals(Optional.of(Duration.ofSeconds(30)), task.timeLeft(clock.instant()),
                "a quarter done in 10 s leaves 30 s");

        executor.resume();
        assertEquals(Optional.empty(), task.timeLeft(clock.instant()), "a Finished Task has none");
    }

    @Test
    void aTaskWithoutProgressHasNoTimeLeft() {
        Task task = start(ctx -> {
            ctx.setStatusText("Sending emails");
            executor.pause();
        });
        executor.runNext();
        clock.advance(Duration.ofSeconds(10));

        assertEquals(Optional.empty(), task.timeLeft(clock.instant()));
    }

    private Task start(Consumer<TaskContext> work) {
        Task task = taskList.start(new Action("Generate report", BlockingMode.NON_BLOCKING));
        executor.execute(() -> task.run(work));
        return task;
    }
}
