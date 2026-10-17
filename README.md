# Vaadin Task Manager

A demo of running long operations from a Vaadin UI. Users start them, follow their progress and get notified
when they finish.

**Live demo:** https://vaadin-task-manager-lljh.onrender.com. It runs on Render's free plan, so the first visit
after a quiet spell can take about a minute while it wakes up.

- Java 21
- Spring Boot 4.1
- Vaadin 25.3

## What it shows

The words in bold are defined in [GLOSSARY.md](GLOSSARY.md).

- **Actions page** (`/`): start each demo **Action** with a regular button. The view starts a **Task** and runs
  its work on a virtual thread. Some report **Progress**, one fails at random and one is a **Blocking Task**, which covers its tab with a
  dialog until it finishes or is cancelled.
- **Tray and task center**: every view has a tray of the session's Tasks with a progress ring, their **Status
  text** and Progress, and a navbar button that says how many Tasks are Running, with a ring for their overall
  Progress. Users can cancel a Running Task, dismiss a **Finished Task** or open it on the Background tasks page.
- **Progress in the tab title**: while Tasks that report Progress are Running, every tab's title starts with
  their overall Progress, for example `45% · Actions`, so users can follow it from another tab.
- **Background tasks page** (`/tasks`): Tasks filtered by **Task state**, with a detail pane for the selected Task
  (its URL is `/tasks/{id}`). From there users can **Retry** a Failed or Cancelled Task, or clear the
  **Task history**.
- **Completion toasts**: when a Task succeeds or fails, every visible tab of the session shows a toast that
  links to the Task's details.
- **Unseen Tasks and native notifications**: a Task that finishes while no tab is visible is **Unseen**. The page
  title counts Unseen Tasks before the Progress, as in `(2) 45% · Actions`, and if the user allowed it from the navbar button, the browser shows a native
  notification.

All browser tabs of one session share the same **Task list**. It is kept in memory with local signals; see
[ADR 0001](docs/adr/0001-session-task-list-uses-local-signals.md).

## Project layout

```text
src/main/java/com/example/taskmanager/
├── task/            # Actions, Tasks and the session's Task list (UI-independent)
├── ui/              # views (the demo Actions are in ActionsView), layout, tray, task center, toasts
└── TaskConfig.java  # the executor, the clock and the session-scoped Task list
```

## Running work as Tasks

An **Action** holds no work. You start a **Task** of it with the work and the executor to run it on, so you
choose the thread, and the work reports through the Task. Every view follows the Task's signals: the tray, the task center,
the Background tasks page and the blocking dialog. The demo is in
[`ActionsView`](src/main/java/com/example/taskmanager/ui/ActionsView.java), and the Actions page shows each
Action's code next to its start button. [ADR 0002](docs/adr/0002-application-runs-the-work-of-tasks.md)
explains why it works this way.

### 1. Define an Action

An **Action** is a name and a `BlockingMode`, nothing more:

```java
static final Action IMPORT_DATA = new Action("Import data", BlockingMode.BLOCKING);
```

Two Actions with the same name and mode are the same Action. The Task list allows at most one Running Task
of it per session.

### 2. Write the work

The work is a `Consumer<TaskContext>`. It can block (I/O, `Thread.sleep`, calls to slow services) and reports
through its context:

| `TaskContext` method       | What it does                                                                 |
|----------------------------|------------------------------------------------------------------------------|
| `setStatusText(String)`    | the short line that says what the Task is doing now                          |
| `setProgress(double)`      | how much is done, from 0 to 1, clamped. Until the first call, the Task is indeterminate |
| `setResultSummary(String)` | what a Succeeded Task produced, for example "2,480 records"                  |
| `isCancelled()`            | whether the user asked it to stop, for work that rarely reports              |

```java
static void importData(TaskContext ctx) {
    for (int file = 1; file <= 8; file++) {
        ctx.setStatusText("Importing file " + file + " of 8");
        importFile(file);
        ctx.setProgress(file / 8.0);
    }
    ctx.setResultSummary("2,480 records");
}
```

### 3. Start a Task

`taskList.start(action, executor, work)` adds a Running Task, hands the work to the executor, and returns the
Task. The executor decides where the work runs:

| Executor                                | The work runs                                              |
|-----------------------------------------|------------------------------------------------------------|
| `Executors.newVirtualThreadPerTaskExecutor()` | on a virtual thread of its own (the demo's `Executor` bean) |
| Spring's `TaskExecutor`, e.g. the `@Async` pool | on that pool's threads                             |
| `Runnable::run`                         | on the calling thread, before `start` returns: a scheduled job or a queue consumer that already has its own thread |

```java
Task task = taskList.start(IMPORT_DATA, executor, ActionsView::importData);
```

The Task always ends. It ends Succeeded when the work returns, Cancelled when the user cancelled it (even before
the work started, in which case the work never runs), and Failed when the work throws or the executor refuses
it. Retry, from the Background tasks page, starts the same work on the same executor. For work that must not
run twice, such as a payment, start it with `Retry.NEVER` and it can't be retried:

```java
taskList.start(CHARGE_CARD, executor, payments::charge, Retry.NEVER);
```

The work reports only through its `TaskContext`. It must not hold on to a view or its components: a Retry,
perhaps from another tab after this one closed, runs the same work again. The demo's work is static methods for
that reason.

The demo injects the `Executor` bean, so that the tests can replace it with one that steps through the work.

### 4. Start it from a view

Use a regular `Button`. Bind it to `taskList.isRunning(action)` so the Action never runs twice, and open a
`BlockingTaskDialog` if the Action is Blocking:

```java
Button button = new Button("Start", e -> BlockingTaskDialog.openIfBlocking(
        taskList.start(IMPORT_DATA, executor, ActionsView::importData)));
Signal<Boolean> running = taskList.isRunning(IMPORT_DATA);
button.bindText(running.map(r -> r ? "Import data running…" : "Start"));
button.bindEnabled(Signal.not(running));
```

A Blocking Task covers the tab that started it until it finishes or the user cancels it. Other tabs of the
session stay usable. Retrying a Blocking Task from the Background tasks page blocks that tab the same way.

Starting an Action that already has a Running Task throws an `IllegalStateException`. A disabled button
never gets there: Vaadin drops clicks on disabled components.

### Failure and Cancellation

- **Failure**: any exception the work throws makes the Task Failed, with the exception's message as its error.
  The stack trace goes to the server log. An `Error`, such as `OutOfMemoryError`, also makes it Failed and is
  then rethrown on the executor's thread. Users can Retry it from the Background tasks page.
- **Cancellation**: when the user cancels, the next `setStatusText`, `setResultSummary` or `setProgress` call
  throws a `CancellationException` and the Task ends Cancelled. Work that rarely reports should check
  `ctx.isCancelled()` and return early. Work that ignores both still ends Cancelled, but only when it returns.
- A Task never stays Running for good: an executor that refuses the work makes it Failed, with "Couldn't start"
  and the reason.

## Running the application

```bash
./mvnw            # default goal is spring-boot:run
```

Then open http://localhost:8080.

## Tests

```bash
./mvnw test       # browserless UI tests and Task unit tests
```

The tests replace the executor and the clock (`TestTaskConfig`), so they step through Tasks without waiting.
They start their own Actions from `TestActionsView`, a test-only copy of the Actions page.

## Production build

```bash
./mvnw package
java -jar target/vaadin-task-manager-1.0-SNAPSHOT.jar
```

## Deploy

`render.yaml` deploys the app as a free [Render](https://render.com) web service built from the `Dockerfile`.
In Render, choose **New → Blueprint** and pick this repository. After that, every push to `main` that passes
the Test workflow redeploys it.

The free instance sleeps after 15 minutes without traffic and takes about a minute to wake up. Sleeping ends
every session and its Task list, since both live in memory.

## Docs

- [GLOSSARY.md](GLOSSARY.md): the domain language used in code, tests and issues
- [docs/adr/](docs/adr/): architecture decisions
- [docs/agents/](docs/agents/): how coding agents work with this repository's issues and labels

Scaffolded from Vaadin's [skeleton-starter-flow-spring](https://github.com/vaadin/skeleton-starter-flow-spring).
