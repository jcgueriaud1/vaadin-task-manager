# TaskPulse demo

A Spring Boot app that shows the TaskPulse add-on at work. It is the live demo at
https://vaadin-task-manager-lljh.onrender.com.

- **Actions page** (`/`): starts each demo Action with a regular button and shows its code. Some report
  Progress, one fails at random and one is a Blocking Task.
- **Background tasks page** (`/tasks`): Tasks filtered by Task state, with a detail pane for the selected Task
  (its URL is `/tasks/{id}`). From there users can Retry a Failed or Cancelled Task, or clear the Task history.
  Every "Open" in the tray, the task center and the toasts leads here.
- `MainLayout` adds the add-on's tray, task center, notification button and watcher, as the add-on's README
  shows.

## Running it

From the repository root:

```bash
./mvnw            # builds the add-on, then runs the demo at http://localhost:8080
./mvnw verify     # tests of the add-on and the demo
```

The demo's tests are browserless UI tests. They replace the executor and the clock (`TestTaskConfig`), so they
step through Tasks without waiting, and start their own Actions from `TestActionsView`, a test-only copy of the
Actions page.

## Production build

```bash
./mvnw -pl demo -am package
java -jar demo/target/taskpulse-demo.jar
```

## Deploy

`render.yaml`, at the repository root, deploys the demo as a free [Render](https://render.com) web service
built from the `Dockerfile`. In Render, choose **New → Blueprint** and pick this repository. After that, every
push to `main` that passes the Test workflow redeploys it.

The free instance sleeps after 15 minutes without traffic and takes about a minute to wake up. Sleeping ends
every session and its Task list, since both live in memory.

Scaffolded from Vaadin's [skeleton-starter-flow-spring](https://github.com/vaadin/skeleton-starter-flow-spring).
