package com.example.taskmanager.task;

/** The TaskContext of an Action that reports Progress. */
public interface ProgressContext extends TaskContext {

    /**
     * Sets how much of the Task is done, from 0 to 1. Values outside are clamped.
     *
     * @throws java.util.concurrent.CancellationException if the Task was cancelled
     */
    void setProgress(double progress);
}
