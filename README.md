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
- **Tray and task center**: every view has a tray of the session's Tasks with their **Status text** and
  Progress, and a navbar counter of Running Tasks. Users can cancel a Running Task or dismiss a **Finished Task**.
- **Background tasks page** (`/tasks`): Tasks filtered by **Task state**, with a detail pane for the selected Task
  (its URL is `/tasks/{id}`). From there users can **Retry** a Failed or Cancelled Task, or clear the
  **Task history**.
- **Completion toasts**: when a Task succeeds or fails, every visible tab of the session shows a toast. The
  toast of a Failed Task links to its details.
- **Unseen Tasks and native notifications**: a Task that finishes while no tab is visible is **Unseen**. The page
  title counts Unseen Tasks, and if the user allowed it from the navbar button, the browser shows a native
  notification.

All browser tabs of one session share the same **Task list**. It is kept in memory with local signals; see
[ADR 0001](docs/adr/0001-session-task-list-uses-local-signals.md).

## Project layout

```text
src/main/java/com/example/taskmanager/
├── task/            # Actions, Tasks and the session's Task list (UI-independent)
├── ui/              # views, layout, tray, task center, toasts
├── TaskConfig.java  # the executor, the clock, the session-scoped Task list and the demo Actions
└── DemoActions.java
```

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
