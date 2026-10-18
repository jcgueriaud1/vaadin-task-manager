package org.vaadin.addons.taskpulse;

/**
 * An Action: something the user can start, defined once from a name, a
 * blocking mode and whether its Tasks can be Retried. It holds no work: the
 * application passes that to {@link PulseTaskList#start(PulseAction, java.util.function.Consumer)},
 * which runs it and returns the Task.
 * <p>
 * Two Actions with the same name, blocking mode and retry are the same Action:
 * the Task list allows at most one Running Task of it per session.
 */
public record PulseAction(String name, BlockingMode blockingMode, Retry retry) {

    /** An Action whose Failed or Cancelled Tasks can be Retried. */
    public PulseAction(String name, BlockingMode blockingMode) {
        this(name, blockingMode, Retry.ALLOWED);
    }

    /** Whether a Task stops the user from working in the view that started it. */
    public enum BlockingMode {
        BLOCKING, NON_BLOCKING
    }

    /** Whether the Action's Tasks can be Retried once they are Failed or Cancelled. */
    public enum Retry {
        /** Retry starts a new Task with the same work on the same executor. */
        ALLOWED,
        /** For work that must not run twice, such as a payment: its Tasks can't be Retried. */
        NEVER
    }
}
