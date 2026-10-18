# The session's Task list uses local signals, not shared signals

The Task list is one per user session and every browser tab of that session sees the same list. We hold it in a `@VaadinSessionScope` Spring bean as a local `ListSignal<Task>`, and each Task holds local `ValueSignal`s for its state, progress and status text. "Shared between tabs" suggests Vaadin's `Shared*Signal`s, but those are built for state shared across sessions and cluster nodes. They require Jackson-serializable values and transactions, which a per-session, in-memory demo doesn't need. Local signals are already thread-safe, work across all UIs of one `VaadinSession`, and their component bindings marshal background updates through `ui.access`. Keeping a separate signal for each field means a progress tick redraws only the progress bar, not the whole list.

## Consequences

- The Task list is not serializable and does not survive session replication or a server restart.
- Moving to Tasks visible to other users would mean switching to `SharedListSignal` with serializable Task snapshots.

## Amended 2026-10-17: the list is a session attribute, not a Spring bean

The Task list now lives in the TaskPulse add-on, which doesn't depend on Spring (ADR 0003). `PulseTaskList.getCurrent()` keeps one per `VaadinSession` as a session attribute, which is what the `@VaadinSessionScope` bean used to give. The demo's Spring bean installs one there, with the demo's clock. The choice of local signals stands.
