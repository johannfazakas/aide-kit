# Spec: deployment

## Purpose

Package and run the application as containers: Gradle-built Docker images for the API service and the web client, and a compose file that brings the pair up together with the assistant configuration supplied via environment.

## Requirements

### Requirement: Service container image
The API service's Docker image SHALL be built by the Gradle build (Jib), on a JRE base image, exposing the API port, with no manual packaging steps. Assistant configuration SHALL arrive via environment variables; without the API key the container fails fast at startup with an error naming the missing variable.

#### Scenario: Service container runs the API
- **WHEN** the service image is built via the Gradle image task and run with the API port published
- **THEN** the task API answers on the published port, honoring the configured environment

### Requirement: Web container image
The web client's Docker image SHALL be built by the Gradle build (a docker build over its Dockerfile, depending on the wasm distribution task), serving the production bundle via a static web server on its own port.

#### Scenario: Web container serves the app
- **WHEN** the web image is built via the Gradle image task and run with its port published
- **THEN** a browser loading that port receives the web client, which calls the service API cross-origin

### Requirement: Compose runs the pair
A compose file at the repository root SHALL start both containers together — the service on its default port 7080 and the web app on 7081 — passing the assistant environment through from the root env file.

#### Scenario: One command brings up the stack
- **WHEN** `docker compose up` runs after `./gradlew buildImages`
- **THEN** the web client is reachable on its port, the API on its port, and the web app's task and chat screens work end-to-end against the service

#### Scenario: Missing key is loud
- **WHEN** the stack starts without the assistant API key
- **THEN** the service container exits with an error naming the missing variable rather than serving a partial API
