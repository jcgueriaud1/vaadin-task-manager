# The application runs the work of Tasks; a Task is only signals

An Action used to carry its work function, and starting it ran the work on the Task list's executor and returned a `CompletableFuture`. A dedicated `ActionButton` started it and followed it. That made the Task list a scheduler, and any work that didn't fit, such as an `@Async` service method, a scheduled job or a queue consumer, had to be wrapped as an Action. Now an Action is only a name and a blocking mode. `TaskList.start(action, retry)` adds a Running Task and returns it, and the application runs the work wherever it likes by calling `task.run(work)`, for example from `Thread.startVirtualThread` or an `@Async` method. The Task's signals are what the tray, the task center, the Background tasks page and the `BlockingTaskDialog` all follow, and a start button is a regular `Button` bound to `taskList.isRunning(action)`.

## Consequences

- The Task list can't re-run work it never had, so Retry goes through a callback given at start. A Task started without one has no Retry.
- Whether a Task reports Progress is no longer declared up front: it is indeterminate until its work first calls `setProgress`.
- There is no `CompletableFuture` result. Work that produces a value hands it on itself.
- A Task whose work is never run stays Running. The application is responsible for running it.

## Amended 2026-10-17: the start takes an Executor

Starting a Task had become a recipe every caller wrote by hand: `start(action, retry)`, then `task.run(work)` on a thread, with a Retry callback that repeated both. Its rules lived only in callers' heads: `run` could be called twice, an `Error` left the Task Running and its Action locked for the session, and the Retry callback held on to the view that started it. Now `TaskList.start(action, executor, work)` is the only way to start a Task. It runs the work once on the given executor, and Retry starts the same work on the same executor. The application still chooses where the work runs, by choosing the Executor. `Runnable::run` covers work that already has its own thread, such as a scheduled job or a queue consumer. The two-step `start(action, retry)` and `task.run(work)` are internal to the Task list.

- Every Task can be Retried, unless it was started with `Retry.NEVER`, for work that must not run twice. This replaces the first consequence above.
- A Task always ends: an `Error` makes it Failed, and so does an executor that refuses the work. A Task cancelled before its work starts ends Cancelled without running it. This replaces the last consequence above.

## Amended 2026-10-17: the Task list has an executor, and Blocking is kept by the start

Every view had to inject an executor and wrap each start and Retry in `openIfBlocking`; a view that forgot the wrap started a Blocking Task that didn't block. Now the Task list has an executor of its own, a virtual thread per Task unless it was created with another, and `start(action, work)` uses it. `start(action, executor, work)` still runs one start elsewhere, so the application still chooses where work runs. A start or a Retry from a browser tab opens the Blocking Task dialog itself; with no current UI it blocks nothing. "Never retried" moved from a start argument to the Action, since it is a property of the work: `new PulseAction(name, mode, Retry.NEVER)`.
