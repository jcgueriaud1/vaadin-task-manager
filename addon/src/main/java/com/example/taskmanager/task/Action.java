package com.example.taskmanager.task;

/**
 * An Action: something the user can start, defined once from a name and a
 * blocking mode. It holds no work: the application runs that itself, however
 * it likes, and reports on it through the Task that
 * {@link TaskList#start(Action, java.util.function.Supplier)} returns.
 * <p>
 * Two Actions with the same name and blocking mode are the same Action: the
 * Task list allows at most one Running Task of it per session.
 */
public record Action(String name, BlockingMode blockingMode) {
}
