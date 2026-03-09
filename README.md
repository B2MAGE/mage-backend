# MAGE Backend

Backend service for the MAGE platform.

Start here:

- New to Spring Boot: read [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md)
- Want to run the project: use the commands in `Getting Started`
- Want to know what already exists: read `Current Repo State`

## Current Repo State

As of March 9, 2026, this repository contains the initial backend setup:

- A single Spring Boot application entry point in [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](src/main/java/com/bdmage/mage_backend/MageBackendApplication.java)
- One basic context-loading test in [src/test/java/com/bdmage/mage_backend/MageBackendApplicationTests.java](src/test/java/com/bdmage/mage_backend/MageBackendApplicationTests.java)
- One config file in [src/main/resources/application.properties](src/main/resources/application.properties)
- Maven wrapper scripts so the team can build without installing Maven globally

## Tech Stack

- Java 21
- Spring Boot 4.0.3
- Spring Web MVC
- Spring Boot DevTools
- Lombok
- JUnit 5 with Spring Boot test support
- Maven Wrapper (`mvnw`, `mvnw.cmd`)

The dependency list lives in [pom.xml](pom.xml).

## Project Layout

```text
mage-backend/
|- docs/
|  `- TEAM_GUIDE.md
|- src/
|  |- main/
|  |  |- java/com/bdmage/mage_backend/
|  |  |  `- MageBackendApplication.java
|  |  `- resources/
|  |     `- application.properties
|  `- test/
|     `- java/com/bdmage/mage_backend/
|        `- MageBackendApplicationTests.java
|- pom.xml
|- mvnw
|- mvnw.cmd
`- README.md
```

## Getting Started

### Prerequisites

- JDK 21 installed
- Git installed

You do not need Maven installed globally because this repo includes the Maven wrapper.

### Run the app

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

By default, Spring Boot runs on `http://localhost:8080`.

### Run tests

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

### Build a jar

Windows PowerShell:

```powershell
.\mvnw.cmd clean package
```

macOS/Linux:

```bash
./mvnw clean package
```

## How The App Starts

The application boots from [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](src/main/java/com/bdmage/mage_backend/MageBackendApplication.java).

- `@SpringBootApplication` tells Spring Boot to start auto-configuration and component scanning
- `SpringApplication.run(...)` starts the application
- Spring scans classes under the `com.bdmage.mage_backend` package

That package choice matters. If you add new controllers, services, or configuration classes, keep them under `com.bdmage.mage_backend` unless you intentionally change the scan setup.

## Configuration

Current application config in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
spring.application.name=mage-backend
```

## Recommended Next Structure

When features start getting added, this is a reasonable package layout to follow:

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

This keeps responsibilities clear and makes it easier for new contributors to find the right place for a change.

## Notes

- If you are new to the framework, read [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md) before building your first feature.
