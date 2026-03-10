# Team Guide

This document is for contributors who are new to Spring Boot and need a practical mental model for this repository.

If you need the local container setup, use [development/docker.md](development/docker.md). This guide focuses on the Spring Boot codebase itself.

## 1. What Spring Boot Is

Spring Boot is a Java framework for building backend applications. It handles common setup automatically so we can focus on writing application code.

For this repo, that mainly means:

- Spring starts the application for us
- Spring creates and wires objects together for us
- Spring can expose Java methods as HTTP endpoints
- Spring can later connect to databases, validation, security, and other infrastructure

## 2. What Exists In This Repo Right Now

There are a few core pieces in place already:

- [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](../src/main/java/com/bdmage/mage_backend/MageBackendApplication.java): the application entry point
- [src/main/resources/application.properties](../src/main/resources/application.properties): application configuration
- [src/main/resources/db/migration](../src/main/resources/db/migration): versioned SQL migrations applied by Flyway
- [src/test/java/com/bdmage/mage_backend/MageBackendApplicationTests.java](../src/test/java/com/bdmage/mage_backend/MageBackendApplicationTests.java): an integration test that checks startup, PostgreSQL connectivity, and the initial migration

That means this project currently starts, but it does not do useful backend work yet.

## 3. The Core Idea To Understand

Spring Boot applications are usually built as layers.

Typical flow:

1. A request hits a `Controller`
2. The controller calls a `Service`
3. The service contains business logic
4. The service may call a `Repository` to read or write data
5. The result is returned as JSON

Not every feature uses every layer, but this is the default structure we should aim for.

## 4. What `@SpringBootApplication` Does

The class [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](../src/main/java/com/bdmage/mage_backend/MageBackendApplication.java) is the main starting point.

```java
@SpringBootApplication
public class MageBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MageBackendApplication.class, args);
    }
}
```

Important behavior:

- It launches the app
- It turns on Spring Boot auto-configuration
- It scans the package `com.bdmage.mage_backend` and its subpackages

Practical rule:

- Put new Spring-managed classes under `com.bdmage.mage_backend`

If you create a class outside that package tree, Spring usually will not find it automatically.

## 5. How To Run The Project

```powershell
docker compose up --build
```

Detailed Docker instructions live in [development/docker.md](development/docker.md).

## 6. Current Dependencies And Why They Matter

The dependencies are declared in [pom.xml](../pom.xml).

- `spring-boot-starter-webmvc`: lets us build HTTP APIs with controllers and JSON responses
- `spring-boot-starter-data-jpa`: adds persistence support and database integration
- `spring-boot-flyway`, `flyway-core`, and `flyway-database-postgresql`: enable and run versioned schema migrations at startup
- `spring-boot-devtools`: improves local development by supporting restarts
- `lombok`: reduces boilerplate like getters, setters, and constructors
- `postgresql`: PostgreSQL JDBC driver for the local database connection
- `spring-boot-starter-webmvc-test`: gives us testing support for Spring MVC applications

## 7. What To Add When You Build A Feature

If you are adding a normal API feature, use this checklist:

1. Create or update a `controller` class for the endpoint
2. Create or update a `service` class for the business logic
3. Add request/response DTOs if the API shape is not trivial
4. Add or update a Flyway migration in [src/main/resources/db/migration](../src/main/resources/db/migration) if the feature changes the database schema
5. Add tests for the new behavior
6. Add configuration only if the feature requires it

When persistence is involved, we will also add:

1. Model/entity classes
2. Repository interfaces
3. Matching database migration files

## 8. Suggested Package Layout

As the repo grows, use a structure close to this:

```text
com.bdmage.mage_backend
|- config
|- controller
|- service
|- repository
|- model
|- dto
|- exception
`- util
```

Reasoning:

- `controller`: HTTP endpoints
- `service`: backend logic
- `repository`: persistence access
- `model`: domain objects or entities
- `dto`: request/response payload classes
- `config`: Spring configuration
- `exception`: custom exceptions and error handling

## 9. Testing Expectations

Right now the only test checks whether the Spring application context loads.

As features are added, we should expand into:

- Controller tests for HTTP behavior
- Service tests for business logic
- Repository tests once persistence exists

Good default rule:

- If you add behavior, add or update a test in the same PR

## 10. Current Gaps In The Project

Be aware of these gaps before assuming the backend already supports them:

- No REST endpoints
- No domain entities or repository implementations yet
- No authentication or authorization
- No validation layer
- No API documentation like Swagger/OpenAPI
- No production configuration

## 11. Working Style For This Repo

Until the project is more mature, keep changes simple and explicit:

- Prefer clear class names over clever abstractions
- Keep controllers thin and move logic into services
- Do not mix HTTP concerns with database concerns in the same class
- Keep package placement consistent so other group members can find things quickly
- Update the README or this guide if the project structure changes in a meaningful way
