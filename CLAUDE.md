# Vaadin Task Manager

Vaadin Flow + Spring Boot application (Java 21, Spring Boot 4.1, Vaadin 25.3).
Code lives in `com.example.taskmanager`.

- Run: `./mvnw` (default goal is `spring-boot:run`), then open http://localhost:8080
- Test: `./mvnw test` (browserless UI tests)
- Package: `./mvnw package`

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues on `jcgueriaud1/vaadin-task-manager`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
