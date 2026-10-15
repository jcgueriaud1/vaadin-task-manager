package com.example.taskmanager.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TaskTimingTest {

    private static final Instant START = Instant.parse("2026-10-15T09:00:00Z");

    private final TestExecutor executor = new TestExecutor();
    private final TestClock clock = new TestClock(START);
    private final TaskList taskList = new TaskList(executor, clock);

    @Test
    void aRunningTaskHasRunSinceItStartedAndAFinishedOneKeepsHowLongItTook() {
        var action = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
            executor.pause();
            clock.advance(Duration.ofSeconds(30));
            return null;
        });
        action.start(taskList);
        Task task = task();
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
        var action = new AsyncProgressAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> {
            clock.advance(Duration.ofSeconds(2));
            ctx.setProgress(0.05);
            executor.pause();
            clock.advance(Duration.ofSeconds(8));
            ctx.setProgress(0.25);
            executor.pause();
            ctx.setProgress(1);
            return null;
        });
        action.start(taskList);
        Task task = task();
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
        var action = new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
            executor.pause();
            return null;
        });
        action.start(taskList);
        executor.runNext();
        clock.advance(Duration.ofSeconds(10));

        assertEquals(Optional.empty(), task().timeLeft(clock.instant()));
    }

    private Task task() {
        return taskList.tasks().peekValues().findFirst().orElseThrow();
    }
}
