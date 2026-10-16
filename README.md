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

## Deploy

`render.yaml` deploys the app as a free [Render](https://render.com) web service built from the `Dockerfile`.
In Render, choose **New → Blueprint** and pick this repository; after that, every push to `main` that passes
the Test workflow redeploys it.

The free instance sleeps after 15 minutes without traffic and takes about a minute to wake up. Sleeping ends
every session and its Task list, since both live in memory.
