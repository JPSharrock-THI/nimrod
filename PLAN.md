# Nimrod — FlatBuffers CSV Decoder CLI

## Overview

A CLI tool that decodes FlatBuffer-serialised columns from CSV exports (e.g. from
DBeaver/GDB) and outputs them as JSON. Distributed as a **GraalVM native image** for
instant startup (~10ms).

```
DBeaver → Export GDB table as CSV → nimrod decode --csv export.csv → JSON
```

Schemas are sourced from the `sup-server-db-fbs-schema` Maven artifact
(`com.bytro.sup:sup-server-db-fbs-schema:0.2.17`), which provides pre-compiled
FlatBuffer Java classes under `com.bytro.sup.fbs.db.*`. The tool reads the 4-byte
`file_identifier` from each FlatBuffer blob and automatically matches it to the correct
deserializer — no manual `--schema` flag needed.

## Tech Stack

| Component        | Choice                          | Rationale                                         |
|------------------|---------------------------------|---------------------------------------------------|
| Language         | Java 21                         | LTS, mature FlatBuffers support                   |
| Framework        | Spring Boot 3.x (no web)       | Familiar, easy DI, `CommandLineRunner`            |
| Build            | Gradle (Kotlin DSL)             | Fat JAR + GraalVM native image                    |
| Native image     | GraalVM + native-build-tools    | AOT compilation → single binary, ~10ms startup    |
| CLI parsing      | Picocli + spring-boot starter   | Rich CLI UX: subcommands, help text, validation   |
| CSV              | Apache Commons CSV               | Battle-tested, handles edge cases                 |
| FlatBuffers      | google/flatbuffers Java library | Runtime FlatBuffer support                        |
| FBS Schemas      | sup-server-db-fbs-schema 0.2.17 | Pre-compiled Java classes from Bytro Nexus        |
| JSON output      | Jackson                          | Already bundled with Spring Boot                  |

## Module Structure

```
nimrod/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/...
├── src/main/
│   ├── java/com/nimrod/
│   │   ├── NimrodApplication.java        # @SpringBootApplication (no web)
│   │   ├── cli/
│   │   │   └── DecodeCommand.java        # Picocli @Command — arg parsing, orchestration
│   │   ├── csv/
│   │   │   └── CsvReader.java            # Read CSV, extract rows + binary columns
│   │   ├── flatbuffers/
│   │   │   ├── FbDecoder.java            # Decodes FlatBuffer blobs using compiled schema classes
│   │   │   └── SchemaRegistry.java       # Maps file_identifier → root type deserializer
│   │   └── output/
│   │       └── JsonWriter.java           # Row maps → JSON (stdout or file)
│   └── resources/
│       ├── application.properties        # spring.main.web-application-type=none
│       └── META-INF/native-image/
│           └── reflect-config.json       # GraalVM reflection hints for FBS classes
└── src/test/java/com/nimrod/
    └── ...                               # Unit tests
```

## CLI Interface

```bash
# Simplest — auto-detects columns, auto-matches schema via file_identifier
nimrod decode --csv export.csv

# Explicit column targeting
nimrod decode --csv export.csv --column payload

# List all known schemas and their file_identifiers
nimrod schemas

# Compact JSON to a file
nimrod decode --csv export.csv --format compact --output decoded.json

# NDJSON piped to jq
nimrod decode --csv export.csv --format ndjson | jq '.payload'

# Also works as a fat JAR (without native image)
java -jar nimrod.jar decode --csv export.csv
```

### Arguments

| Argument       | Required | Default           | Description                                                                 |
|----------------|----------|-------------------|-----------------------------------------------------------------------------|
| `--csv`        | Yes      | —                 | Path to CSV export file                                                     |
| `--column`     | No       | auto              | Column name(s) containing FlatBuffer blobs. Omit to auto-detect binary data |
| `--encoding`   | No       | base64            | Encoding of binary data in CSV: `base64`, `hex`, or `raw`                   |
| `--format`     | No       | pretty            | Output format: `pretty` (default), `compact`, or `ndjson`                   |
| `--output`     | No       | stdout            | Output file path (default: print to console)                                |

## Implementation Phases

### Phase 1 — Project Skeleton & Schema Registry
- Spring Boot console app, Gradle build, fat JAR
- Picocli wired up with `decode` and `schemas` subcommands
- Arg parsing and validation
- `sup-server-db-fbs-schema` Maven dependency from Bytro Nexus (compiled FlatBuffer classes)
- `SchemaRegistry`: register all known FlatBuffer root types from the schema artifact,
  build a `Map<String, SchemaEntry>` keyed by 4-byte file_identifier

### Phase 2 — CSV Ingestion
- Read CSV with Apache Commons CSV
- If `--column` provided: target those columns
- If `--column` omitted: auto-detect binary columns (trial-decode against schema, log findings)
- Decode Base64/hex/raw binary data from string cells
- Pass through non-binary columns as-is

### Phase 3 — FlatBuffer Decoding
- Read bytes 4–7 from buffer to extract the `file_identifier`
- Look up the identifier in `SchemaRegistry` → get the matching deserializer
- If no match: error with a list of all known identifiers and their schema types
- Use the compiled FlatBuffer Java classes (e.g. `FbsDbArmy.getRootAsFbsDbArmy(buffer)`)
  to deserialize, following the same patterns as sup-server's serde layer
- Produce `Map<String, Object>` per decoded buffer
- Handle nested tables, vectors, unions, enums

### Phase 4 — JSON Output
- Replace binary column value with decoded map
- Serialize full row as JSON via Jackson
- Pretty JSON (default), compact JSON, NDJSON modes via `--format`
- Write to stdout or file via `--output`

### Phase 5 — GraalVM Native Image
- Add `org.graalvm.buildtools.native` Gradle plugin
- Generate reflection metadata for all 22 FBS root classes (needed because
  `SchemaRegistry` uses `Class.forName` and `Method.invoke` at runtime)
- Configure `reflect-config.json` or use GraalVM reachability metadata
- `./gradlew nativeCompile` → produces `nimrod` binary
- Test that the native binary works identically to the fat JAR
- CI: build native images for macOS arm64, macOS x64, Linux x64, Windows x64

### Phase 6 — Polish
- Error handling and user-friendly messages
- Progress indicator for large CSVs
- Unit and integration tests
- README with usage examples

## Design Decisions (finalised)

### 1. Schema handling → Compiled classes + auto-detection via file_identifier
Schemas come from the `sup-server-db-fbs-schema` Maven artifact
(`com.bytro.sup:sup-server-db-fbs-schema:0.2.17`), published to Bytro Nexus. This
artifact contains pre-compiled FlatBuffer Java classes under `com.bytro.sup.fbs.db.*`
(e.g. `FbsDbArmy`, `FbsDbPoint`, `FbsDbTradeState`).

At startup, `SchemaRegistry` registers all known root types and their 4-byte
`file_identifier` values (e.g. `"FBSA"` → `FbsDbArmy`) to build a lookup map.

When decoding, the tool reads the 4-byte identifier from each FlatBuffer blob (bytes 4–7)
and automatically selects the correct deserializer. No manual schema flag needed.

If the identifier is unknown, the tool errors with a clear message listing all available
schemas and their identifiers.

We use the compiled Java classes rather than raw `.fbs` reflection:
- Matches exactly how sup-server's serde layer works (proven, production-tested)
- Strongly typed — compile-time safety for field access
- No need for a `flatc` compiler at runtime or raw `.fbs` files on disk
- To support new schemas, bump the `sup-server-db-fbs-schema` version

### 2. Column detection → Explicit flag with auto-detect fallback
- When `--column` is provided: decode only those named columns
- When `--column` is omitted: scan each column for Base64/hex patterns and attempt trial-decode against the schema. Columns that successfully decode as valid FlatBuffers are treated as binary
- Auto-detect logs which columns it identified so the user can verify

### 3. Binary encoding → Base64 default, hex and raw supported
- **Base64** (default) — DBeaver's typical export format for binary columns
- **Hex** — alternate encoding some tools use
- **Raw** — for CSVs where binary bytes are written directly (edge case)
- Controlled via `--encoding` flag

### 4. Output format → Pretty JSON default, compact and NDJSON available
- **Pretty** (default) — human-readable, indented JSON array. Best for inspection
- **Compact** — minified JSON array. Smaller file size
- **NDJSON** — one JSON object per line, no wrapping array. Best for piping into `jq`, streaming, or loading into other tools
- Controlled via `--format` flag

### 5. Why Spring Boot for a CLI?
- Familiar to the team
- DI makes testing easy
- Picocli integration via `picocli-spring-boot-starter` is seamless
- `spring.main.web-application-type=none` keeps it lightweight
- Spring Boot 3.x has first-class GraalVM native image support

### 6. Distribution → GraalVM native image (primary), fat JAR (fallback)
The tool is distributed as a **GraalVM native image** — a single platform-specific
binary with no JVM dependency. This gives ~10ms startup time, making it feel like
a native CLI tool.

```
./gradlew nativeCompile      → build/native/nativeCompile/nimrod
./gradlew bootJar            → build/libs/nimrod.jar (fallback)
```

**Reflection and native image:** `SchemaRegistry` uses `Class.forName()` and
`Method.invoke()` to dynamically load FBS classes. GraalVM needs these declared
at build time via `reflect-config.json`. Since the 22 root classes are enumerated
in `SchemaRegistry.ROOT_CLASS_NAMES`, the config can be generated automatically
or maintained as a static resource.

**Fat JAR remains available** as a fallback for environments where a native binary
isn't practical (e.g. running from CI, unsupported platforms).


## Example Output

```json
[
  {
    "id": 1,
    "name": "player_login",
    "payload": {
      "eventType": "LOGIN",
      "timestamp": 1708873200,
      "userId": "abc-123",
      "metadata": {
        "platform": "ios",
        "version": "2.4.1"
      }
    }
  },
  {
    "id": 2,
    "name": "purchase",
    "payload": {
      "eventType": "PURCHASE",
      "amount": 499,
      "currency": "USD",
      "itemId": "sword_of_fire"
    }
  }
]
```
