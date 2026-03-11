# Contributing

A good contribution is small enough to review and clear enough that another developer can extend it without re-learning your intent from scratch.

## Read Before You Change Code

- [README.md](README.md)
- [docs/getting-started.md](docs/getting-started.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/engineering-standards.md](docs/engineering-standards.md)
- [docs/operations.md](docs/operations.md)

## Standard Workflow

1. Pull the latest `main` branch.
2. Create a focused branch for one feature, fix, or refactor.
3. Start the backend locally and confirm the current behavior.
4. Make the change in the correct layer.
5. Add or update a Flyway migration if the schema changes.
6. Add or update tests for the behavior you changed.
7. Update documentation if setup, configuration, architecture, or operations changed.
8. Run the full test suite before opening a pull request.

## Pull Request Expectations

Every pull request should answer these questions clearly:

- What changed?
- Why was the change needed?
- How was it tested?
- Does it change the schema, environment variables, or public API?
- What should a reviewer pay special attention to?

Keep pull requests focused:

- avoid combining unrelated cleanup work with behavioral changes in the same pull request.
- do not rename files or packages unless the change provides a clear architectural or maintainability benefit.
- avoid including formatting-only changes unless the pull request is specifically intended to address formatting.

## Definition of Done

A backend change is ready to merge when:

- the code fits the repository's layer boundaries
- tests cover the changed behavior
- schema changes are captured in Flyway migrations
- configuration changes are documented
- the change is understandable to someone who did not write it

## Quality Checks

Run the full Maven wrapper test command before requesting review:

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

Because the suite uses Testcontainers, Docker must be running.

## Review Mindset

When you review someone else's backend change, look for:

- correctness
- architectural fit
- maintainability
- missing tests
- migration or configuration risk
- API contract clarity

The goal is not to find style nits in isolation. The goal is to keep the codebase predictable as it grows.
