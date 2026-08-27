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
- Create text posts and browse a paginated feed of your own and friends' posts
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

### 1) Start PostgreSQL

The repository provides a local PostgreSQL 16 container with database and user
names that match the backend defaults:

```bash
docker compose up -d postgres
```

It exposes PostgreSQL at `localhost:5433` with:

- Database: `connectify`
- Username: `connectify`
- Password: `connectify`

Change this password before using anything other than a local development environment.

For a separately managed database, set these environment variables before
starting the backend:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/connectify"
$env:DB_USERNAME = "connectify"
$env:DB_PASSWORD = "your-secure-password"
```

Alternatively, copy `.env.example` to `.env` in the repository root and adjust the values:

```bash
cp .env.example .env
```

Do not wrap the password in quotes; quotes become part of the password value.

### 2) Start the backend

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

### 3) Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on the Vite dev URL (typically `http://localhost:5173`) and calls the backend at `http://localhost:8080`.

## Database Configuration

The backend uses PostgreSQL by default at `jdbc:postgresql://localhost:5433/connectify`.
Datasource settings read from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, with local Docker-friendly defaults. Do not use the default password outside local development.

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

Changing `POSTGRES_PASSWORD` in `docker-compose.yml` does not update an existing database volume.

## Authentication

- JWT-based authentication is enabled.
- Replace `app.jwt.secret` with a secure key before any production deployment.

