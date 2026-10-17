# TaskPulse is a plain-Vaadin add-on whose generic types carry the `Pulse` prefix

The Task list, the tray, the task center, the toasts and the Blocking Task dialog are useful to any Vaadin app, so they are published as an add-on, **TaskPulse** (`org.vaadin.addons:taskpulse`, package `org.vaadin.addons.taskpulse`). The repository becomes a Maven reactor with two modules: `addon/`, the jar released to Maven Central and the Vaadin Directory, and `demo/`, the Spring Boot app deployed to Render. The demo keeps its Actions page, its Background tasks page and its layout.

The domain words, Task, Task list and Action, are what every app would name its own classes, so in code each public type carries the `Pulse` prefix: `PulseTask`, `PulseTaskList`, `PulseTaskContext`, `PulseAction`, `PulseTray`, `PulseCenter`, `PulseTab` and so on. The value types that belong to one of them are nested in it instead: `PulseTask.State`, `PulseTask.Outcome`, `PulseTask.Retry`, `PulseAction.BlockingMode`. The glossary keeps the plain domain words; the prefix is a packaging concern. We considered keeping the bare names and relying on the package, which is the most readable but clashes on import with other `Task` classes, and renaming the concepts themselves, which would have made the glossary and the UI text worse.

The add-on depends on Vaadin only, not on Spring, so Quarkus and plain servlet apps can use it. Its per-session Task list is a `VaadinSession` attribute, created on first use by `PulseTaskList.getCurrent()`; an app that wants another clock installs its own first, with `PulseTaskList.install`. The demo still injects it as a session-scoped Spring bean, which installs one with the demo's clock so its tests can drive time.

## Consequences

- The prefix is part of the published API: dropping or changing it later is a breaking release.
- What linked the tray, the task center and the toasts to the demo's Background tasks page is now an open handler the app sets, and "View all" a view-all handler. Without one, they show no such button.
- The demo's Background tasks page builds its rows from `PulseTaskParts`, the pieces the tray uses, which an app can use for its own views of Tasks.
- The tab-level behaviour that `MainLayout` held (tracking tab visibility for Unseen, the Progress in the page title, native notifications, toasts) moves into the add-on; see the amendment below.
- The add-on ships its own stylesheet for its components; the demo keeps only its pages' styles.

## Amended 2026-10-17: one `PulseTab` per UI, set up by the components

Wiring the add-on took about six calls in the main layout: the tray, the task center, the notification button, a watcher, and the same open handler three times. Each forgotten call failed silently; without the watcher, every Finished Task stayed Unseen. `PulseWatcher` is replaced by `PulseTab`, one per `UI`, which is one per browser tab. It is set up by the first TaskPulse component that attaches to the UI (the task center or the notification button), or by `PulseTab.current()`. It adds the tray to the UI, tracks the tab for Unseen, writes the page title, shows toasts and native notifications, and holds the one open handler and view-all handler that every surface uses. We considered keeping it per layout, but a tab is what Unseen and the page title are about, and keying it to the UI means a second setup returns the same one.

- The tray, the task center and the toasts have no handlers of their own, and the tray has no public constructor. Turn it off with `PulseTab.setTrayEnabled(false)`.
- Registering a tab is internal to `PulseTab`. A Task is Unseen only if the session has at least one tracked tab and none of them is visible, so a Task list with no TaskPulse UI never marks anything Unseen.
- "Enable notifications" is in the task center's popover. `PulseNotificationButton` stays for apps without a task center.
- The page title prefix is on by default; `PulseTab.setPageTitleUpdates(false)` leaves the title to the app.
