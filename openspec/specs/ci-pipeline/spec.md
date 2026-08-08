# Spec: ci-pipeline

## Purpose

Run the Gradle build (compile, ktlint check, and tests) automatically on pushes and pull requests via GitHub Actions, without secrets and without calling an LLM.

## Requirements

### Requirement: GitHub Actions build workflow
The repository SHALL contain a GitHub Actions workflow that runs on pushes to `main` and on pull requests, executing the Gradle build (compile, ktlint check, and tests) on JDK 21 with Gradle caching enabled. The workflow SHALL NOT require any secrets, and nothing it runs may call an LLM.

#### Scenario: Pull request triggers build
- **WHEN** a pull request is opened or updated
- **THEN** the workflow runs `./gradlew build` and reports success or failure on the PR

#### Scenario: Lint violation fails the workflow
- **WHEN** the workflow runs on a commit containing a ktlint violation
- **THEN** the build step fails and the workflow is marked failed

#### Scenario: Test failure fails the workflow
- **WHEN** the workflow runs on a commit with a failing unit or integration test
- **THEN** the build step fails and the workflow is marked failed
