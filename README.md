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

- **Actions page** (`/`): start each demo **Action**. Each one runs as a **Task** on its own virtual thread.
  Some report **Progress**, one fails at random and one is a **Blocking Task**, which covers its tab with a
  dialog until it finishes or is cancelled.
- **Tray and task center**: every view has a tray of the session's Tasks with a progress ring, their **Status
  text** and Progress, and a navbar button that says how many Tasks are Running, with a ring for their overall
  Progress. Users can cancel a Running Task, dismiss a **Finished Task** or open it on the Background tasks page.
- **Background tasks page** (`/tasks`): Tasks filtered by **Task state**, with a detail pane for the selected Task
  (its URL is `/tasks/{id}`). From there users can **Retry** a Failed or Cancelled Task, or clear the
  **Task history**.
- **Completion toasts**: when a Task succeeds or fails, every visible tab of the session shows a toast that
  links to the Task's details.
- **Unseen Tasks and native notifications**: a Task that finishes while no tab is visible is **Unseen**. The page
  title counts Unseen Tasks, and if the user allowed it from the navbar button, the browser shows a native
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

## Creating Async Actions

An **Action** is defined once from three things: a name, a `BlockingMode` and a work function. The work runs
on a virtual thread, so it can block (I/O, `Thread.sleep`, calls to slow services). It reports back through
the context it gets. The demo Actions are `static final` constants in
[`ActionsView`](src/main/java/com/example/taskmanager/ui/ActionsView.java), and the Actions page shows each
one's code next to its start button.

Keep each Action in a constant or a singleton. `TaskList` recognises an Action by identity, which is how
it allows at most one Running Task per Action and how Retry finds the Action again. If you create the
Action inside a view's constructor, every tab and every visit gets a new one.

There are two classes, depending on whether the Action reports **Progress**:

| Class                    | Context the work gets | What the work can do                                       |
|--------------------------|-----------------------|------------------------------------------------------------|
| `AsyncAction<T>`         | `TaskContext`         | `setStatusText`, `setResultSummary`, `isCancelled`         |
| `AsyncProgressAction<T>` | `ProgressContext`     | the same, plus `setProgress(double)` from 0 to 1           |

Either one can be `BlockingMode.NON_BLOCKING` or `BlockingMode.BLOCKING`, which gives four kinds of Action.

### Non-blocking, without Progress

The tray shows a spinning ring and the Status text.

```java
static final AsyncAction<Integer> SEND_NEWSLETTER = new AsyncAction<>(
        "Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
            ctx.setStatusText("Sending emails");
            int sent = mailer.sendAll();            // any blocking work
            ctx.setResultSummary("Sent to " + sent + " subscribers");
            return sent;
        });
```

### Non-blocking, with Progress

Use `AsyncProgressAction` and call `setProgress` as the work goes. Values outside 0..1 are clamped.

```java
static final AsyncProgressAction<Integer> GENERATE_REPORT = new AsyncProgressAction<>(
        "Generate report", BlockingMode.NON_BLOCKING, ctx -> {
            for (int page = 1; page <= 10; page++) {
                ctx.setStatusText("Rendering page " + page + " of 10");
                renderPage(page);
                ctx.setProgress(page / 10.0);
            }
            ctx.setResultSummary("10 pages");
            return 10;
        });
```

### Blocking

`BlockingMode.BLOCKING` marks a Task that the user must wait for. The Action doesn't block the tab by
itself. The view that starts it opens a `BlockingTaskDialog`, which covers the tab until the Task finishes
or the user cancels it. Other tabs of the session stay usable.

```java
static final AsyncProgressAction<Integer> IMPORT_DATA = new AsyncProgressAction<>(
        "Import data", BlockingMode.BLOCKING, ctx -> { /* ... */ return 2480; });

Button start = new Button("Start", e -> {
    Task task = IMPORT_DATA.startTask(taskList);
    if (IMPORT_DATA.blockingMode() == BlockingMode.BLOCKING) {
        new BlockingTaskDialog(task).open();
    }
});
```

### Starting an Action

Inject the session's `TaskList` into the view. Then either:

- call `action.startTask(taskList)`, which returns the `Task`, for example to open a `BlockingTaskDialog`, or
- call `action.start(taskList)`, which returns a `CompletableFuture<T>` with the work's value. Cancelling the
  future cancels the Task.

`taskList.isRunning(action)` is a signal you can bind to the button, so it is disabled while the Action has a
Running Task. Starting an Action that is already running throws an `IllegalStateException`.

### Failure and Cancellation

- **Failure**: any exception the work throws makes the Task Failed, with the exception's message as its error.
  Users can Retry it from the Background tasks page.
- **Cancellation**: when the user cancels, the next `setStatusText`, `setResultSummary` or `setProgress` call
  throws a `CancellationException` and the Task ends Cancelled. Work that rarely reports should check
  `ctx.isCancelled()` and return early. Work that ignores both still ends Cancelled, but only when it returns.

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
