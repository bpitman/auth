# auth

A multi-project SBT build containing:

- **auth-core** — A Java library
- **auth-backend** — A Scala application (cross-compiled for 2.13 and 3) that depends on `auth-core`
- **auth-frontend** — A Scala Spring Boot server that serves a React (Vite + TypeScript) client application

## Build

```bash
sbt compile
sbt test
```

## Cross-compile the Scala app

```bash
sbt +auth-backend/compile
```

## Build the frontend UI

```bash
cd auth-frontend/ui && npm install && npm run build
```

Then run the frontend server:

```bash
sbt auth-frontend/run
```

Visit http://localhost:3000 to see the app.
