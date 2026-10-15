package com.example.taskmanager.task;

/** What an Action's work function can do with the Task it runs in. */
public interface TaskContext {

    /**
     * Sets the short line that says what the Task is doing now.
     *
     * @throws java.util.concurrent.CancellationException if the Task was cancelled
     */
    void setStatusText(String statusText);

    /** Whether the user asked the Task to stop, for work that reports rarely. */
    boolean isCancelled();
}
