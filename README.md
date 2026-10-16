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

`BlockingMode.BLOCKING` marks a Task that the user must wait for. When it starts from a tab, a
`BlockingTaskDialog` covers that tab until the Task finishes or the user cancels it. Other tabs of the session
stay usable. Retrying a Blocking Task from the Background tasks page blocks that tab the same way.

```java
static final AsyncProgressAction<Integer> IMPORT_DATA = new AsyncProgressAction<>(
        "Import data", BlockingMode.BLOCKING, ctx -> { /* ... */ return 2480; });
```

### Starting an Action from a view

Inject the session's `TaskList` into the view and add an `ActionButton`:

```java
add(new ActionButton(IMPORT_DATA, taskList));            // says "Start"
add(new ActionButton(IMPORT_DATA, taskList, "Import"));  // your own text
```

The button does the rest:

- while the Action has a Running Task, in any tab of the session, it says "Import data running…" and is
  disabled, so the Action never runs twice
- a click starts a Task, and opens the `BlockingTaskDialog` if the Action is Blocking
- its accessible name is "Start Import data", whatever its text.

It is a regular Vaadin `Button`, so you can add theme variants, an icon or a tooltip.

### Starting an Action from code

Outside a view, for example from a scheduled job or a service, call the Action directly. These never open a
dialog:

- `action.startTask(taskList)` returns the `Task`.
- `action.start(taskList)` returns a `CompletableFuture<T>` with the work's value. Cancelling the future
  cancels the Task.

Starting an Action that already has a Running Task throws an `IllegalStateException`.

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
