# Nimrod — FlatBuffers CSV Decoder CLI

## Overview

A Spring Boot console application that decodes FlatBuffer-serialised columns from CSV exports (e.g. from DBeaver/GDB) and outputs them as JSON.

```
DBeaver → Export GDB table as CSV → java -jar nimrod.jar decode --csv export.csv → JSON
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
| Build            | Gradle (Kotlin DSL)             | Fat JAR packaging via Spring Boot plugin           |
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
│       └── application.properties        # spring.main.web-application-type=none
└── src/test/java/com/nimrod/
    └── ...                               # Unit tests
```

## CLI Interface

```bash
# Simplest — auto-detects columns, auto-matches schema via file_identifier
java -jar nimrod.jar decode --csv export.csv

# Explicit column targeting
java -jar nimrod.jar decode --csv export.csv --column payload

# List all known schemas and their file_identifiers
java -jar nimrod.jar schemas

# Compact JSON to a file
java -jar nimrod.jar decode --csv export.csv --format compact --output decoded.json

# NDJSON piped to jq
java -jar nimrod.jar decode --csv export.csv --format ndjson | jq '.payload'
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

### Phase 5 — Polish
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
