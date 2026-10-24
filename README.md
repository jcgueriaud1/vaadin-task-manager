# TaskPulse

A Vaadin add-on for running long work in the background from a Vaadin UI. Users start it, follow its progress
and get notified when it finishes, in every browser tab of their session.

**Live demo:** https://vaadin-task-manager-lljh.onrender.com. It runs on Render's free plan, so the first visit
after a quiet spell can take about a minute while it wakes up.

- Java 21
- Vaadin 25.3. The add-on depends on Vaadin only, not on Spring.

## What you get

The words in bold are defined in [GLOSSARY.md](GLOSSARY.md).

- **Tasks you start from your own code**: an **Action** is a name and a blocking mode. You start a **Task** of
  it with its work, which runs on a virtual thread or on the executor you choose, and the work reports its
  **Status text**, **Progress**, **Result summary** and **Result file** as it goes. An Action has at most one Running Task per session.
- **Task center** (`PulseCenter`): a navbar button that says how many Tasks are Running, with a ring for their
  overall Progress and a badge for **Unseen** Tasks. It opens a popover with the Running and recent Finished
  Tasks, and the button that turns on native notifications.
- **Tray** (`PulseTray`): a card in the bottom-right of every view with the session's Tasks, a progress ring
  each and their overall Progress. Users can cancel a Running Task, dismiss a **Finished Task** or open it.
- **Blocking Tasks**: a **Blocking Task** covers the tab that started it with a dialog until it finishes or the
  user cancels it, and so does its **Retry**. Other tabs of the session stay usable.
- **One `PulseTab` per browser tab**, set up by the task center, that
  - adds the tray;
  - starts the tab's title with the overall Progress, for example `45% · Orders`;
  - marks a Task that finishes while no tab is visible as Unseen, and counts those in the title too, as in
    `(2) 45% · Orders`;
  - shows a toast in every visible tab when a Task succeeds or fails, and a native notification for each Unseen
    Task once the user allowed them.

All browser tabs of one session share the same **Task list**. It is kept in memory with local signals; see
[ADR 0001](docs/adr/0001-session-task-list-uses-local-signals.md).

## Installation

```xml
<dependency>
    <groupId>org.vaadin.addons</groupId>
    <artifactId>taskpulse</artifactId>
    <version>1.0.0</version>
</dependency>
```

Turn on server push, so that updates from background threads reach the browser right away:

```java
@Push
public class Application implements AppShellConfigurator {
}
```

## Quick start

Put the task center in your main layout's navbar. It sets up the tab's `PulseTab`, which adds the tray, the
toasts, native notifications and the progress in the page title:

```java
public class MainLayout extends AppLayout {

    public MainLayout() {
        addToNavbar(new DrawerToggle(), new PulseCenter());
        PulseTab.current()
                .setOpenHandler(task -> UI.getCurrent().navigate(TaskView.class,
                        new RouteParameters("taskId", String.valueOf(task.id()))))
                .setViewAllHandler(() -> UI.getCurrent().navigate(TaskView.class));
    }
}
```

The open handler is what "Open" does everywhere: in the task center, the tray, the toasts, and a click on a
native notification. "View all" in the task center runs the view-all handler. Without a handler, its buttons
are hidden.

`PulseTab` has two more settings:

| Setting                          | Default | What it does                                                       |
|----------------------------------|---------|--------------------------------------------------------------------|
| `setTrayEnabled(boolean)`        | on      | the tray in the bottom-right                                       |
| `setPageTitleUpdates(boolean)`   | on      | the Unseen count and overall Progress at the start of the page title |

There is one `PulseTab` per `UI`, that is per browser tab. The task center and `PulseNotificationButton` set it
up when they attach. An app without either sets it up by calling `PulseTab.current()`. The tab shows the
session's Task list, `PulseTaskList.getCurrent()`, which is created on first use and kept as a
`VaadinSession` attribute.

## Running work as Tasks

An Action holds no work. You start a Task of it with the work, which runs on the Task list's executor unless
you pass another, and the work reports through the Task. The tray, the task center and the blocking dialog follow the
Task's signals. [ADR 0002](docs/adr/0002-application-runs-the-work-of-tasks.md) explains why it works this
way.

### 1. Define an Action

An Action is a name and a `BlockingMode`, nothing more:

```java
static final PulseAction IMPORT_DATA = new PulseAction("Import data", BlockingMode.BLOCKING);
```

For work that must not run twice, such as a payment, mark the Action `Retry.NEVER`: its Tasks can't be retried.

```java
static final PulseAction CHARGE_CARD = new PulseAction("Charge card", BlockingMode.BLOCKING, Retry.NEVER);
```

Two Actions with the same name, mode and retry setting are the same Action. The Task list allows at most one
Running Task of it per session.

### 2. Write the work

The work is a `Consumer<PulseTaskContext>`. It can block (I/O, `Thread.sleep`, calls to slow services) and
reports through its context:

| `PulseTaskContext` method  | What it does                                                                 |
|----------------------------|------------------------------------------------------------------------------|
| `setStatusText(String)`    | the short line that says what the Task is doing now                          |
| `setProgress(double)`      | how much is done, from 0 to 1, clamped. Until the first call, the Task is indeterminate |
| `setResultSummary(String)` | what a Succeeded Task produced, for example "2,480 records"                  |
| `setResultFile(Path)`      | a file a Succeeded Task produced. The toast, the tray, the task center and `PulseTaskParts.resultFile(task)` link to it, and it opens in a new browser tab |
| `isCancelled()`            | whether the user asked it to stop, for work that rarely reports              |

```java
static void importData(PulseTaskContext ctx) {
    for (int file = 1; file <= 8; file++) {
        ctx.setStatusText("Importing file " + file + " of 8");
        importFile(file);
        ctx.setProgress(file / 8.0);
    }
    ctx.setResultSummary("2,480 records");
}
```

Work that writes a file hands it over with `setResultFile`, and the user opens it from the Task once it
Succeeds, from any tab of the session. The file must still exist then: deleting old ones is up to the app.

```java
static void exportCustomers(PulseTaskContext ctx) {
    Path file = writeCustomers(ctx); // slow: reports its Progress as it goes
    ctx.setResultSummary("1,240 customers");
    ctx.setResultFile(file);
}
```

### 3. Start a Task

`taskList.start(action, work)` adds a Running Task, runs the work on the Task list's executor, and returns the
Task. By default, each Task's work runs on a virtual thread of its own.

```java
PulseTask task = PulseTaskList.getCurrent().start(IMPORT_DATA, Imports::importData);
```

Started from a browser tab, a Task of a Blocking Action covers that tab with a dialog until it finishes or the
user cancels it. Started with no current UI, for example from a scheduled job, it blocks nothing.

To run the work elsewhere, pass an executor, either to the start or once to the Task list:

| Where                                      | How                                                         |
|--------------------------------------------|-------------------------------------------------------------|
| one start, e.g. Spring's `@Async` pool     | `taskList.start(action, executor, work)`                    |
| on the calling thread, before `start` returns: a scheduled job or a queue consumer with its own thread | `taskList.start(action, Runnable::run, work)` |
| every start of the session's Task list     | `PulseTaskList.install(session, new PulseTaskList(clock, executor))`, see [With Spring](#with-spring) |

The Task always ends. It ends Succeeded when the work returns, Cancelled when the user cancelled it (even before
the work started, in which case the work never runs), and Failed when the work throws or the executor refuses
it. `task.retry()` starts the same work on the same executor.

The work reports only through its `PulseTaskContext`. It must not hold on to a view or its components: a Retry,
perhaps from another tab after this one closed, runs the same work again. Static methods are a good fit.

#### Choosing an executor

Each Task runs only on the executor it was started with, and its state is safe to update from any thread, so
one app can mix executors. What changes from one to another:

- **Retry uses the same executor.** If that executor has been shut down since, for example during an app
  shutdown, the retried Task ends Failed with "Couldn't start".
- **A bounded pool queues work, and a queued Task shows as Running.** It is Running from the start, with a
  spinning ring, until a thread picks up its work. Cancelling it in the queue ends it Cancelled without running
  it. The default virtual threads never queue.
- **`Runnable::run` in a view freezes the tab.** The work runs in the click handler, so the tab gets no update,
  and no Blocking dialog, until it returns. Use it only for code that already runs on its own thread.
- **Thread-bound context is not carried over by default.** Spring Security's authentication, transactions and
  logging context reach the work only if the executor copies them; Spring's executors can be set up to,
  virtual threads don't. Work that reads the current user can then succeed on one executor and fail on
  another, so pass the work what it needs rather than reading it from the thread.

Choose the executor by the kind of work, and set it once: for the whole Task list with
`new PulseTaskList(clock, executor)`, or for one start.

### 4. Start it from a view

Use a regular `Button`. Bind it to `taskList.isRunning(action)` so the Action never runs twice:

```java
PulseTaskList taskList = PulseTaskList.getCurrent();
Button button = new Button("Start", e -> taskList.start(IMPORT_DATA, Imports::importData));
Signal<Boolean> running = taskList.isRunning(IMPORT_DATA);
button.bindText(running.map(r -> r ? "Import data running…" : "Start"));
button.bindEnabled(Signal.not(running));
```

Starting an Action that already has a Running Task throws an `IllegalStateException`. A disabled button
never gets there: Vaadin drops clicks on disabled components.

### Failure and Cancellation

- **Failure**: any exception the work throws makes the Task Failed, with the exception's message as its error.
  The stack trace goes to the server log. An `Error`, such as `OutOfMemoryError`, also makes it Failed and is
  then rethrown on the executor's thread.
- **Cancellation**: when the user cancels, the next `setStatusText`, `setResultSummary` or `setProgress` call
  throws a `CancellationException` and the Task ends Cancelled. Work that rarely reports should check
  `ctx.isCancelled()` and return early. Work that ignores both still ends Cancelled, but only when it returns.
- A Task never stays Running for good: an executor that refuses the work makes it Failed, with "Couldn't start"
  and the reason.

## Your own views of Tasks

`PulseTaskList` exposes the session's Tasks as read-only signals: `tasks()` (oldest first), `runningCount()`,
`overallProgress()` and `unseenCount()`, plus `find(id)` and `clearFinished()`. It keeps at most 50 Tasks: when
it is full, a start removes the oldest Finished Task, and throws if all 50 are Running.

Each `PulseTask` has signals for its `state()`, `progress()`, `statusText()`, `outcome()`, `retryable()` and
more, and the user's actions on it: `cancel()`, `retry()` and `dismiss()`. `PulseTaskParts` builds the same pieces the tray
uses, such as a Task's indicator, its meta line or its progress bar, for a page of your own. The demo's
Background tasks page is built that way.

## With Spring

The add-on needs no Spring configuration. To inject the Task list, or to give it another clock or executor,
install it in the session from a session-scoped bean:

```java
@Bean
@VaadinSessionScope
public PulseTaskList taskList(Clock clock, Executor taskExecutor) {
    return PulseTaskList.install(VaadinSession.getCurrent(), new PulseTaskList(clock, taskExecutor));
}
```

`install` must run before anything else uses the session's Task list.

## Styling

The components load `taskpulse/taskpulse.css` from the add-on jar. Its classes all start with `pulse-`. Its
colors and sizes come from Aura when it is the theme, and from Lumo otherwise. Override the `--pulse-*` custom
properties to change them, for example:

```css
html {
  --pulse-accent: #7c3aed;
}
```

## Repository layout

```text
addon/   # the add-on: org.vaadin.addons:taskpulse, package org.vaadin.addons.taskpulse
demo/    # the demo app (Spring Boot), deployed to Render; see demo/README.md
docs/    # ADRs and how coding agents work with this repository
```

```bash
./mvnw            # builds the add-on and runs the demo at http://localhost:8080
./mvnw verify     # tests of both modules
```

ADR 0003 explains the split and the `Pulse` prefix:
[docs/adr/0003-taskpulse-is-a-plain-vaadin-add-on.md](docs/adr/0003-taskpulse-is-a-plain-vaadin-add-on.md).

## Releasing

1. Push a tag `taskpulse-X.Y.Z`. The Release workflow sets the version from the tag and publishes the add-on
   and its parent pom to Maven Central. The demo is not published.
2. Publish a GitHub release for that tag. The Vaadin Directory workflow uploads the add-on zip to the Directory
   as a draft, to review and publish there.

The workflows need these repository secrets: `CENTRAL_USER`, `CENTRAL_TOKEN`, `OSSRH_GPG_SECRET_KEY`,
`OSSRH_GPG_SECRET_KEY_PASSWORD` and `DIRECTORY_AUTH_KEY`.

To build the Directory zip locally: `./mvnw -Pdirectory -pl addon -am package`. It lands in `addon/target/`.

## Docs

- [GLOSSARY.md](GLOSSARY.md): the domain language used in code, tests and issues
- [docs/adr/](docs/adr/): architecture decisions
- [docs/agents/](docs/agents/): how coding agents work with this repository's issues and labels

## License

Apache License 2.0, see [LICENSE](LICENSE).
