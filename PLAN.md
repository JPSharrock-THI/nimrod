# Nimrod — FlatBuffers CSV Decoder CLI

## Overview

A Spring Boot console application that decodes FlatBuffer-serialised columns from CSV exports (e.g. from DBeaver/GDB) and outputs them as JSON.

```
DBeaver → Export GDB table as CSV → java -jar nimrod.jar decode --csv export.csv → JSON
```

Schemas are sourced from a separate Git repo (submodule). The tool reads the 4-byte
`file_identifier` from each FlatBuffer blob and automatically matches it to the correct
`.fbs` schema — no manual `--schema` flag needed.

## Tech Stack

| Component        | Choice                          | Rationale                                         |
|------------------|---------------------------------|---------------------------------------------------|
| Language         | Java 21                         | LTS, mature FlatBuffers support                   |
| Framework        | Spring Boot 3.x (no web)       | Familiar, easy DI, `CommandLineRunner`            |
| Build            | Gradle (Kotlin DSL)             | Fat JAR packaging via Spring Boot plugin           |
| CLI parsing      | Picocli + spring-boot starter   | Rich CLI UX: subcommands, help text, validation   |
| CSV              | Apache Commons CSV               | Battle-tested, handles edge cases                 |
| FlatBuffers      | google/flatbuffers Java library | Reflection-based decoding (no codegen per schema) |
| JSON output      | Jackson                          | Already bundled with Spring Boot                  |

## Module Structure

```
nimrod/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/...
├── schemas/                              # Git submodule → external .fbs schema repo
│   ├── research_state.fbs                # file_identifier "FBSR"
│   ├── army.fbs                          # file_identifier "FBSA"
│   └── ...
├── src/main/
│   ├── java/com/nimrod/
│   │   ├── NimrodApplication.java        # @SpringBootApplication (no web)
│   │   ├── cli/
│   │   │   └── DecodeCommand.java        # Picocli @Command — arg parsing, orchestration
│   │   ├── csv/
│   │   │   └── CsvReader.java            # Read CSV, extract rows + binary columns
│   │   ├── flatbuffers/
│   │   │   ├── FbDecoder.java            # Schema-driven reflection decoder
│   │   │   └── SchemaRegistry.java       # Scans .fbs files, builds file_identifier → schema map
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

# Override schema directory (default: bundled submodule)
java -jar nimrod.jar decode --csv export.csv --schema-dir /path/to/schemas

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
| `--schema-dir` | No       | bundled schemas/  | Path to directory of `.fbs` files (overrides the bundled submodule)          |
| `--column`     | No       | auto              | Column name(s) containing FlatBuffer blobs. Omit to auto-detect binary data |
| `--encoding`   | No       | base64            | Encoding of binary data in CSV: `base64`, `hex`, or `raw`                   |
| `--format`     | No       | pretty            | Output format: `pretty` (default), `compact`, or `ndjson`                   |
| `--output`     | No       | stdout            | Output file path (default: print to console)                                |

## Implementation Phases

### Phase 1 — Project Skeleton & Schema Registry
- Spring Boot console app, Gradle build, fat JAR
- Picocli wired up with `decode` and `schemas` subcommands
- Arg parsing and validation
- Git submodule for external `.fbs` schema repo
- `SchemaRegistry`: scan all `.fbs` files, parse `file_identifier` directives,
  build a `Map<String, SchemaEntry>` keyed by 4-byte identifier

### Phase 2 — CSV Ingestion
- Read CSV with Apache Commons CSV
- If `--column` provided: target those columns
- If `--column` omitted: auto-detect binary columns (trial-decode against schema, log findings)
- Decode Base64/hex/raw binary data from string cells
- Pass through non-binary columns as-is

### Phase 3 — FlatBuffer Decoding
- Read bytes 4–7 from buffer to extract the `file_identifier`
- Look up the identifier in `SchemaRegistry` → get the matching `.fbs` schema
- If no match: error with a list of all known identifiers and their schema files
- Use FlatBuffers reflection API to walk the binary buffer
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

### 1. Schema handling → Auto-detection via file_identifier
Schemas live in a separate Git repo, pulled in as a submodule under `schemas/`.
At startup, `SchemaRegistry` scans all `.fbs` files and extracts their `file_identifier`
(e.g. `"FBSR"`) to build a lookup map.

When decoding, the tool reads the 4-byte identifier from each FlatBuffer blob (bytes 4–7)
and automatically selects the correct schema. No `--schema` flag needed.

If the identifier is unknown, the tool errors with a clear message listing all available
schemas and their identifiers.

We use FlatBuffers reflection/schema parsing rather than compiled Java classes.
- One JAR works with any `.fbs` schema — no rebuild needed per game/table
- `--schema-dir` override lets users point at a local checkout if needed
- If reflection proves too painful, fallback option is requiring codegen + classpath of generated classes

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
