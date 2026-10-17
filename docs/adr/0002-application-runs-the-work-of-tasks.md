# The application runs the work of Tasks; a Task is only signals

An Action used to carry its work function, and starting it ran the work on the Task list's executor and returned a `CompletableFuture`. A dedicated `ActionButton` started it and followed it. That made the Task list a scheduler, and any work that didn't fit, such as an `@Async` service method, a scheduled job or a queue consumer, had to be wrapped as an Action. Now an Action is only a name and a blocking mode. `TaskList.start(action, retry)` adds a Running Task and returns it, and the application runs the work wherever it likes by calling `task.run(work)`, for example from `Thread.startVirtualThread` or an `@Async` method. The Task's signals are what the tray, the task center, the Background tasks page and the `BlockingTaskDialog` all follow, and a start button is a regular `Button` bound to `taskList.isRunning(action)`.

## Consequences

- The Task list can't re-run work it never had, so Retry goes through a callback given at start. A Task started without one has no Retry.
- Whether a Task reports Progress is no longer declared up front: it is indeterminate until its work first calls `setProgress`.
- There is no `CompletableFuture` result. Work that produces a value hands it on itself.
- A Task whose work is never run stays Running. The application is responsible for running it.
