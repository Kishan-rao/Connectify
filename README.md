# Connectify

A full-stack social networking web application:

- `frontend` — React + Vite client
- `backend` — Spring Boot REST API with JWT auth and JPA

## Tech Stack

- Frontend: React, React Router, Axios, Vite
- Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA
- Database: PostgreSQL 16 (default for local development); H2 is retained only for automated tests

## Features

- JWT-based registration and login with BCrypt password hashing
- Protected feed, friends, and profile pages
- Create text posts and browse a paginated feed of your own, friends', and shared-group members' posts
- Feed transparency: "Why am I seeing this?" explanations for each feed post
- Friend requests: send, accept, decline, view accepted friends, and receive friends-of-friends suggestions
- User profiles with account details, friend and post counts, and recent posts
- Persistent PostgreSQL storage for accounts, posts, and friendships across backend and frontend restarts

## Project Structure

```text
Connectify/
├── backend/                    # Spring Boot API
│   ├── src/main/java/.../      # Controllers, services, entities, security
│   ├── src/main/resources/     # application.properties
│   ├── src/test/               # Integration tests (H2)
│   ├── gradle/wrapper/         # Gradle wrapper
│   ├── build.gradle
│   └── settings.gradle
├── frontend/                   # React + Vite app
│   ├── public/                 # Static assets (favicon)
│   ├── src/
│   │   ├── api/                # Axios client
│   │   ├── components/         # Shared UI components
│   │   ├── context/            # Auth context
│   │   ├── pages/              # Route pages
│   │   └── styles/             # Page-level CSS
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── docker-compose.yml          # Local PostgreSQL
├── .env.example                # Environment variable template
├── LICENSE
└── README.md
```

## Prerequisites

- Java 21
- Node.js 18+ (recommended: latest LTS)
- npm
- Docker Desktop (recommended for local PostgreSQL), or PostgreSQL 16+

## Run Locally

### 1) Configure environment variables

Copy `.env.example` to `.env` in the repository root and fill in your values:

```bash
cp .env.example .env
```

The required variables are:

| Variable       | Description                                   | Example value                        |
|----------------|-----------------------------------------------|--------------------------------------|
| `DB_URL`       | JDBC URL for PostgreSQL                       | `jdbc:postgresql://localhost:5432/connectify` |
| `DB_USERNAME`  | PostgreSQL username                           | `connectify`                         |
| `DB_PASSWORD`  | PostgreSQL password                           | `<your-password>`                    |
| `JWT_SECRET`   | Base64-encoded HMAC-SHA256 signing key (≥ 256 bit) | `<your-generated-secret>`       |

**Do not wrap values in quotes; quotes become part of the value.**

#### Generating a JWT secret

```bash
# Linux / macOS
openssl rand -base64 48
```

```powershell
# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { [byte](Get-Random -Max 256) }))
```

The application will **refuse to start** if `JWT_SECRET` is not set, preventing accidental use of an insecure default.

### 2) Start PostgreSQL

The repository provides a local PostgreSQL 16 container with database and user
names that match the backend defaults:

```bash
docker compose up -d postgres
```

It exposes PostgreSQL at `localhost:5432` with:

- Database: `connectify`
- Username: `connectify`
- Password: see `POSTGRES_PASSWORD` in `docker-compose.yml` / your `.env`

Change the password before using anything other than a local development environment.

For a separately managed database, set the `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
environment variables (or update your `.env` file) to point to your instance.

### 3) Start the backend

```bash
cd backend
./gradlew bootRun
```

On Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

Backend runs on `http://localhost:8080`.

### 4) Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on the Vite dev URL (typically `http://localhost:5173`) and calls the backend at `http://localhost:8080`.

## Database Configuration

The backend uses PostgreSQL by default at `jdbc:postgresql://localhost:5432/connectify`.
Datasource settings are read from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

Hibernate uses `ddl-auto=update` for local development. Tests use an isolated H2 in-memory database via `backend/src/test/resources/application.properties`.

### Persistent local data

The Docker service stores database files in the named `connectify-postgres-data` volume. Your accounts, posts, friendships, and feed data survive frontend restarts, backend restarts, and normal Docker stop/start operations.

Use the following to stop the database without deleting data:

```bash
docker compose down
```

Do **not** run `docker compose down -v` unless you intentionally want to permanently delete all local PostgreSQL data.

### Change the local database password

For an already-created Docker database, change the password inside PostgreSQL and set the same value in `.env`:

```powershell
docker compose exec postgres psql -U connectify -d connectify -c "ALTER USER connectify WITH PASSWORD 'your-secure-password';"
```

```properties
DB_PASSWORD=your-secure-password
```


Changing `POSTGRES_PASSWORD` in `docker-compose.yml` does not update an existing database volume; you must use the SQL command above instead.

## Authentication

- JWT-based authentication is enabled.
- The signing key is loaded from the `JWT_SECRET` environment variable; the application will not start without it.
- Never commit a real `JWT_SECRET` value to version control.
