# Vaadin Task Manager

A Vaadin Flow + Spring Boot application, scaffolded from Vaadin's official
[skeleton-starter-flow-spring](https://github.com/vaadin/skeleton-starter-flow-spring).

- Java 21
- Spring Boot 4.1
- Vaadin 25.3

## Running the application

```bash
./mvnw            # default goal is spring-boot:run
```

Then open http://localhost:8080.

## Tests

```bash
./mvnw test       # browserless UI unit tests
```

## Production build

```bash
./mvnw package
java -jar target/vaadin-task-manager-1.0-SNAPSHOT.jar
```
