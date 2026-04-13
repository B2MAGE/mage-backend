# Contributing

Keep changes small, focused, and easy to reason about.

## Read First

Before changing backend code, read:

- [README.md](README.md)
- [docs/README.md](docs/README.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/engineering-standards.md](docs/engineering-standards.md)

Use [docs/operations.md](docs/operations.md) when the change affects setup, runtime behavior, or troubleshooting guidance.

## Standard Workflow

1. Pull the latest `main`.
2. Create a focused branch.
3. Start the backend and confirm the current behavior.
4. Make the change in the correct layer.
5. Add or update tests.
6. Add a Flyway migration if the schema changes.
7. Update docs if setup, architecture, or operations changed.
8. Run the relevant checks before opening a pull request.

## Pull Request Expectations

Every PR should explain:

- what changed
- why it changed
- how it was tested
- whether it changes schema, configuration, or public API
- any reviewer risk areas

Keep PRs reviewable:
- avoid unrelated cleanup in behavior-changing PRs
- do not mix refactors and feature work unless they are tightly coupled
- avoid file churn that does not materially help the change

## Definition of Done

A backend change is ready to merge when:

- the code fits the repository’s layers
- tests cover the changed behavior
- schema changes are represented with Flyway migrations
- config or workflow changes are documented
- the change is understandable to a reviewer without extra verbal context

## Quality Checks

Run the backend test suite before requesting review.

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

Docker must be running because integration tests use Testcontainers.

## Review Mindset

Prioritize:
- correctness
- architectural fit
- migration and configuration risk
- missing tests
- API contract clarity

Style concerns matter, but they are secondary to behavior and maintainability.
