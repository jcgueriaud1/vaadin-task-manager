package com.example.taskmanager.task;

/** The TaskContext of an Action that reports Progress. */
public interface ProgressContext extends TaskContext {

    /** Sets how much of the Task is done, from 0 to 1. Values outside are clamped. */
    void setProgress(double progress);
}
