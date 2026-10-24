---
name: taskpulse
description: Use when code depends on the TaskPulse Vaadin add-on (org.vaadin.addons:taskpulse, package org.vaadin.addons.taskpulse, Pulse* types), or when adding long-running background work with progress, cancellation, a task tray or notifications to a Vaadin Flow app.
---

# TaskPulse

TaskPulse runs long work in the background from a Vaadin Flow UI (Java 21, Vaadin 25, no Spring needed). Users follow it in a task center, a tray, toasts and native notifications, in every browser tab of their session. Its public types carry the `Pulse` prefix; the domain words are Action, Task, Task list.

## Setup

1. Add `org.vaadin.addons:taskpulse` and turn on server push: `@Push` on the `AppShellConfigurator`.
2. Put `new PulseCenter()` in the main layout's navbar. It sets up the tab's `PulseTab`, which adds the tray, the toasts, native notifications and the progress in the page title.
3. Set what "Open" and "View all" do, once per tab:

```java
PulseTab.current()
        .setOpenHandler(task -> UI.getCurrent().navigate(TaskView.class,
                new RouteParameters("taskId", String.valueOf(task.id()))))
        .setViewAllHandler(() -> UI.getCurrent().navigate(TaskView.class));
```

Without a handler, its buttons are hidden. An app with no `PulseCenter` calls `PulseTab.current()` itself.

## Running work as a Task

```java
static final PulseAction IMPORT_DATA = new PulseAction("Import data", BlockingMode.BLOCKING);

static void importData(PulseTaskContext ctx) {          // static: holds no view
    for (int file = 1; file <= 8; file++) {
        ctx.setStatusText("Importing file " + file + " of 8");
        importFile(file);
        ctx.setProgress(file / 8.0);                     // 0..1; indeterminate until the first call
    }
    ctx.setResultSummary("2,480 records");               // ctx.setResultFile(path) for a file to open
}

// In a view: a regular Button, bound so the Action never runs twice
PulseTaskList taskList = PulseTaskList.getCurrent();
Button button = new Button("Start", e -> taskList.start(IMPORT_DATA, Imports::importData));
button.bindEnabled(Signal.not(taskList.isRunning(IMPORT_DATA)));
```

- An Action is only a name, a `BlockingMode` and a `Retry`. Equal Actions are the same Action, and it has at most one Running Task per session: a second `start` throws `IllegalStateException`.
- `start(action, work)` runs the work on a virtual thread. `start(action, executor, work)` runs one start elsewhere; `Runnable::run` runs it on the calling thread, for code that already has its own thread (a scheduled job, a queue consumer), never from a click handler.
- A Blocking Action started from a browser tab covers that tab with a dialog until it finishes or is cancelled. Started with no current UI, it blocks nothing.
- The work reports only through its `PulseTaskContext`. A Retry, perhaps from another tab, runs the same work again on the same executor, so pass the work what it needs (the current user, ids) rather than reading thread-bound context or holding components.
- Work that must not run twice, such as a payment: `new PulseAction(name, mode, Retry.NEVER)`.

## How a Task ends

- Returns: Succeeded. Throws: Failed, with the exception's message; the stack trace goes to the log.
- User cancels: the next `setStatusText`, `setProgress` or `setResultSummary` throws `CancellationException` and the Task ends Cancelled. Work that reports rarely checks `ctx.isCancelled()`.
- The executor refuses the work: Failed, "Couldn't start". A Task never stays Running for good.
- A Result file must still exist when the user opens it; deleting old files is up to the app.

## Own views and configuration

- `PulseTaskList` signals: `tasks()` (oldest first, at most 50), `runningCount()`, `overallProgress()`, `unseenCount()`, `isRunning(action)`; plus `find(id)` and `clearFinished()`.
- `PulseTask` signals: `state()`, `progress()`, `determinate()`, `statusText()`, `outcome()`, `retryable()`; actions `cancel()`, `retry()`, `dismiss()`.
- `PulseTaskParts` builds the tray's pieces (`indicator`, `meta`, `progressBar`, `resultSummary`, `resultFile`, `errorMessage`) for a page of your own.
- Another clock or executor for the whole session: `PulseTaskList.install(session, new PulseTaskList(clock, executor))` before anything uses the session's Task list. With Spring, from a `@VaadinSessionScope` bean.
- `PulseTab.setTrayEnabled(false)`, `setPageTitleUpdates(false)` turn off the tray and the title prefix.
- Styling: override the `--pulse-*` custom properties, for example `html { --pulse-accent: #7c3aed; }`. Classes start with `pulse-`.

Full guide: https://github.com/jcgueriaud1/vaadin-task-manager/blob/main/README.md. API reference: the add-on's Javadoc (`-javadoc.jar`, package overview in `org.vaadin.addons.taskpulse`).
