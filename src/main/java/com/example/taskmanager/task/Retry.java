package com.example.taskmanager.task;

/** Whether a Task can be Retried once it is Failed or Cancelled. */
public enum Retry {
    /** Retry starts a new Task with the same work on the same executor. */
    ALLOWED,
    /** For work that must not run twice, such as a payment: the Task can't be Retried. */
    NEVER
}
