package org.vaadin.addons.taskpulse;

import java.nio.file.Path;

/** What the work of a Task can report while it runs. */
public interface PulseTaskContext {

    /**
     * Sets the short line that says what the Task is doing now.
     *
     * @throws java.util.concurrent.CancellationException if the Task was cancelled
     */
    void setStatusText(String statusText);

    /**
     * Sets how much of the Task is done, from 0 to 1. Values outside are clamped.
     * Until the work first calls this, the Task shows as indeterminate.
     *
     * @throws java.util.concurrent.CancellationException if the Task was cancelled
     */
    void setProgress(double progress);

    /**
     * Sets the short text a Succeeded Task ends with, describing what it produced.
     *
     * @throws java.util.concurrent.CancellationException if the Task was cancelled
     */
    void setResultSummary(String resultSummary);

    /**
     * Sets the file a Succeeded Task ends with, which the user opens in a new
     * browser tab from the toast, the tray, the task center or the app's own
     * views. Browsers download types they can't show. The file must still exist
     * when the user opens it: deleting it is up to the application.
     *
     * @throws java.util.concurrent.CancellationException if the Task was cancelled
     */
    void setResultFile(Path resultFile);

    /** Whether the user asked the Task to stop, for work that reports rarely. */
    boolean isCancelled();
}
