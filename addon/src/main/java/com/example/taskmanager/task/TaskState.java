package com.example.taskmanager.task;

/** Where a Task is in its life. A Task starts Running and ends in one of the other three. */
public enum TaskState {
    RUNNING, SUCCEEDED, FAILED, CANCELLED
}
