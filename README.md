# auth

A multi-project SBT build containing:

- **java-lib** — A Java library
- **app** — A Scala application (cross-compiled for 2.13 and 3) that depends on `java-lib`

## Build

```bash
sbt compile
sbt test
```

## Cross-compile the Scala app

```bash
sbt +app/compile
```
