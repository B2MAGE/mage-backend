# Install Docker Compose

This guide explains how to install Docker Compose for local backend development.

For this repository, the recommended setup is Docker Desktop. Docker's current documentation says Docker Desktop is the easiest and recommended way to get Docker Compose, and that it includes Docker Compose together with Docker Engine and Docker CLI.

## Recommended Option: Docker Desktop

Official Docker installation pages:

- Windows: https://docs.docker.com/desktop/setup/install/windows-install/
- macOS: https://docs.docker.com/desktop/setup/install/mac-install/
- Linux: https://docs.docker.com/desktop/setup/install/linux/

General Docker Compose install overview:

- https://docs.docker.com/compose/install/

### Windows

1. Open the official Windows install page.
2. Download Docker Desktop.
3. Run `Docker Desktop Installer.exe`.
4. Follow the installer prompts.
5. Start Docker Desktop after installation.

Docker's current Windows docs note that Docker Desktop on Windows supports current Windows 10 and 11 desktop editions.

### macOS

1. Open the official macOS install page.
2. Download Docker Desktop.
3. Open `Docker.dmg`.
4. Drag Docker into the Applications folder.
5. Start Docker Desktop.

### Linux Desktop

1. Open the official Linux install page for your distribution.
2. Install Docker Desktop for your distro.
3. Start Docker Desktop from your desktop environment or with:

```bash
systemctl --user start docker-desktop
```

## Alternative Option: Linux Compose Plugin

If Docker Engine and Docker CLI are already installed on Linux, Docker documents a separate Compose plugin install path.

Official plugin instructions:

- https://docs.docker.com/compose/install/linux/

### Ubuntu and Debian

```bash
sudo apt-get update
sudo apt-get install docker-compose-plugin
```

### RPM-based distributions

```bash
sudo yum update
sudo yum install docker-compose-plugin
```

This path is for Linux only.

## Verify the Installation

After installation, verify that Docker and Compose are available:

```bash
docker --version
docker compose version
```

If Docker Desktop is installed, start it before running backend commands.

You should then be able to run:

```bash
docker compose up --build
```

## Next Step

Once Docker Compose is installed, continue with:

- [Tag Filter Testing](../tag-filter-testing/README.md)
- [Getting Started](../getting-started.md)
