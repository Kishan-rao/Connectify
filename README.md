# Social Network (Web App)

This repository contains a full-stack social networking web application:

- `frontend`: React + Vite client
- `backend`: Spring Boot REST API with JWT auth, WebSocket support, and JPA

## Tech Stack

- Frontend: React, React Router, Axios, Vite
- Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA
- Database: H2 (default in-memory), PostgreSQL configuration available (commented)

## Project Structure

```text
Social-Network-master/
  backend/      # Spring Boot API
  frontend/     # React app
```

## Prerequisites

- Java 21
- Node.js 18+ (recommended: latest LTS)
- npm

## Run Locally

### 1) Start the backend

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

### 2) Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on the Vite dev URL (typically `http://localhost:5173`) and calls the backend at `http://localhost:8080`.

## Database Configuration

Default backend config uses H2 in-memory DB (`application.properties`), so no external DB is required for local development.

To switch to PostgreSQL, update and uncomment the PostgreSQL datasource properties in:

- `backend/src/main/resources/application.properties`

## Authentication

- JWT-based authentication is enabled.
- Replace `app.jwt.secret` with a secure key before any production deployment.

## Notes

- The folder `Simplified_Social_Networking_System` is a legacy standalone Java version and is not required for running the current web app (`frontend` + `backend`).
