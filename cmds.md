# SwiftMatch — Commands Reference

Running log of every terminal command suggested during the build. Grouped by purpose.
Newer commands are appended to each section; nothing is removed without the same command being deprecated.

Legend:

- `PS>` — PowerShell
- `bash$` — Git Bash (Windows) / Linux / macOS
- `psql>` — inside `psql`

---

## 1. Install toolchain (one-time)

```powershell
# JDK 17 (Temurin)
winget install EclipseAdoptium.Temurin.17.JDK

# Maven 3.9+
winget install --id Apache.Maven --exact

# Docker Desktop (Kafka / Redis / Postgres containers)
winget install Docker.DockerDesktop

# IntelliJ IDEA Community (recommended IDE)
winget install JetBrains.IntelliJIDEA.Community

# k6 (load testing — needed at M6)
winget install k6
```

Troubleshooting:

```powershell
winget --version                    # confirm winget works
winget search maven                 # find the correct package ID
winget list Apache.Maven            # confirm an install landed
```

## 2. Verify the toolchain

```bash
# works in Git Bash and PowerShell
java -version           # expect: openjdk version "17.0.x" (Java 25 also works but not pinned)
mvn -version            # expect: Apache Maven 3.9.x, Java version: 17.x
docker version
docker compose version
```

## 3. Fix `JAVA_HOME` / PATH issues

If `mvn` can't find Java or a fresh terminal can't find `mvn`:

```bash
# Git Bash — session-only unblocker
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.10.7-hotspot"
export PATH="$JAVA_HOME/bin:/c/Program Files/Apache/Maven/bin:$PATH"

# inspect current state
echo $JAVA_HOME
echo $PATH | tr ':' '\n' | grep -i maven
which mvn
which java
```

Permanent fix: set `JAVA_HOME` under Windows "Environment Variables → User variables", ensure `%JAVA_HOME%\bin` is on `Path`, **close every terminal (including IDE-embedded)** and reopen.

## 4. Build the project (Maven)

```powershell
# full build of all 5 modules
.\mvnw.cmd -B verify

# install all modules to ~/.m2 so `-pl` works
.\mvnw.cmd install -DskipTests

# build + run one service (requires common already installed)
.\mvnw.cmd -pl api-service spring-boot:run

# build + run, auto-building deps first (no separate install step)
.\mvnw.cmd -pl api-service -am spring-boot:run
```

Bash equivalent: replace `.\mvnw.cmd` with `./mvnw`.

Generate / regenerate the wrapper:

```powershell
# from Git Bash with proper quoting
mvn -N "wrapper:wrapper" "-Dmaven=3.9.9"

# from PowerShell
mvn -N wrapper:wrapper '-Dmaven=3.9.9'
```

## 5. Infra (Docker Compose)

```powershell
# start everything
docker compose up -d

# start only what's needed for backend dev
docker compose up -d redis postgres kafka kafka-ui

# status
docker compose ps

# logs for one service
docker compose logs kafka --tail=80
docker compose logs kafka -f              # follow

# stop + keep volumes
docker compose down

# stop + NUKE volumes (wipes postgres data, rebuilds creds)
docker compose down -v

# pull fresh images
docker compose pull
docker compose pull kafka
```

Quick container access:

```powershell
# psql into the running postgres
docker exec -it swiftmatch-postgres psql -U swiftmatch -d swiftmatch

# list databases
docker exec -it swiftmatch-postgres psql -U swiftmatch -d swiftmatch -c "\l"

# Redis CLI
docker exec -it swiftmatch-redis redis-cli
```

Check for port conflicts before starting:

```powershell
netstat -ano | Select-String "9092|9094|6379|5432|8080|8081|8084|8085|8090"
```

## 6. Test the running service

```powershell
# PowerShell
Invoke-RestMethod http://localhost:8080/actuator/health

# bash / curl
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus | head
```

Kafka UI in a browser: <http://localhost:8090>

## 7. Git

```powershell
# stage and commit
git add .
git status
git status --ignored                        # see what .gitignore is skipping
git commit -m "feat: M1 scaffold - 4 services + wrapper + compose infra"

# stop tracking a path that slipped through .gitignore
git rm --cached -r target/
git commit -m "chore: stop tracking target/"
```

## 8. Shell differences (PowerShell vs Git Bash)

- **List files (detailed)** — PowerShell: `Get-ChildItem` or `ls` (no `-la`) — Git Bash: `ls -la`
- **Run the Maven wrapper** — PowerShell: `.\mvnw.cmd -B verify` — Git Bash: `./mvnw -B verify`
- **HTTP GET** — PowerShell: `Invoke-RestMethod <url>` — Git Bash: `curl <url>`
- **Search text in output** — PowerShell: `... | Select-String "pat"` — Git Bash: `... | grep pat`
- **Env var (session)** — PowerShell: `$env:NAME = "value"` — Git Bash: `export NAME="value"`

Pick one shell and stick with it to avoid subtle PATH / quoting bugs.

## 9. Troubleshooting quick reference

Check for a local Postgres Windows service stealing port 5432:

```powershell
Get-Service | Where-Object {$_.Name -match "postgres"}
# if a running service appears, our container is being shadowed;
# remap container to 5433 (already done in this repo) or stop the Windows service:
Get-Service -Name "postgresql*" | Stop-Service
```

Prove the container itself accepts the swiftmatch credentials (bypasses Spring):

```powershell
docker exec -it swiftmatch-postgres psql -U swiftmatch -d swiftmatch -c "SELECT current_user;"
```

Check what's listening on dev ports (Postgres / Kafka / app):

```powershell
netstat -ano | Select-String "5432|5433|9092|9094|8080|8081|8084|8085|8090"
Get-Process -Id <PID>     # resolve a PID from netstat output
```

Force-wipe a specific Docker volume if `down -v` didn't catch it:

```powershell
docker volume ls | Select-String swiftmatch
docker volume rm swiftmatch_postgres-data
docker compose up -d postgres
```

```powershell
# Kafka stack trace mentions KafkaDockerWrapper + missing process.roles
# → using apache/kafka image with Bitnami-style KAFKA_CFG_* vars; remove the _CFG_

# "dependency kafka failed to start"
docker compose logs kafka --tail=50

# Postgres "password authentication failed"
docker compose down -v && docker compose up -d

# Maven wrapper "Cannot start maven from wrapper"
# → check .mvn/wrapper/maven-wrapper.properties has a real version URL
```

---

> This file is maintained continuously. Any terminal command suggested in chat is added here
> under the relevant section.
