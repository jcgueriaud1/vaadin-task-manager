package org.vaadin.addons.taskpulse;

/**
 * An Action: something the user can start, defined once from a name and a
 * blocking mode. It holds no work: the application runs that itself, however
 * it likes, and reports on it through the Task that
 * {@link PulseTaskList#start(PulseAction, java.util.concurrent.Executor, java.util.function.Consumer)} returns.
 * <p>
 * Two Actions with the same name and blocking mode are the same Action: the
 * Task list allows at most one Running Task of it per session.
 */
public record PulseAction(String name, BlockingMode blockingMode) {

    /** Whether a Task stops the user from working in the view that started it. */
    public enum BlockingMode {
        BLOCKING, NON_BLOCKING
    }
}
