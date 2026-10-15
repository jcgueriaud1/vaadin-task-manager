package com.example.taskmanager.task;

/** What an Action's work function can do with the Task it runs in. */
public interface TaskContext {

    /** Sets the short line that says what the Task is doing now. */
    void setStatusText(String statusText);
}
