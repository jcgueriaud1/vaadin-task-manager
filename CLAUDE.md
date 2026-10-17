# TaskPulse

A Vaadin add-on (Java 21, Vaadin 25.3, no Spring) and its Spring Boot 4.1 demo, as a Maven reactor:

- `addon/`: the add-on, `org.vaadin.addons:taskpulse`, package `org.vaadin.addons.taskpulse`.
  Its public types carry the `Pulse` prefix (ADR 0003).
- `demo/`: the demo app, package `org.vaadin.addons.taskpulse.demo`, deployed to Render.

- Run: `./mvnw` at the root (builds the add-on, then runs the demo), then open http://localhost:8080
- Test: `./mvnw verify` (add-on unit and browserless tests, demo browserless UI tests)
- Package the demo: `./mvnw -pl demo -am package`
- Directory zip: `./mvnw -Pdirectory -pl addon -am package`

## Agent skills

The skills from `mattpocock/skills` are vendored in `.claude/skills/` (see its README).

### Issue tracker

Issues are tracked in GitHub Issues on `jcgueriaud1/vaadin-task-manager`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
