package com.example.taskmanager.task;

/** How a Finished Task ended. */
public sealed interface Outcome {

    /** The work returned. The Result summary is empty if the work didn't set one. */
    record Succeeded(String resultSummary) implements Outcome {
    }

    /** The work threw, or the executor refused it. */
    record Failed(String errorMessage) implements Outcome {
    }

    /** The user cancelled the Task. */
    record Cancelled() implements Outcome {
    }
}
