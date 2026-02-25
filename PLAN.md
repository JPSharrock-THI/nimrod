# Nimrod — FlatBuffers CSV Decoder CLI

## Overview

A Spring Boot console application that decodes FlatBuffer-serialised columns from CSV exports (e.g. from DBeaver/GDB) and outputs them as JSON.

```
DBeaver → Export GDB table as CSV → java -jar nimrod.jar decode --schema game.fbs --csv export.csv → JSON
```

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
├── src/main/
│   ├── java/com/nimrod/
│   │   ├── NimrodApplication.java        # @SpringBootApplication (no web)
│   │   ├── cli/
│   │   │   └── DecodeCommand.java        # Picocli @Command — arg parsing, orchestration
│   │   ├── csv/
│   │   │   └── CsvReader.java            # Read CSV, extract rows + binary columns
│   │   ├── flatbuffers/
│   │   │   └── FbDecoder.java            # Schema-driven reflection decoder
│   │   └── output/
│   │       └── JsonWriter.java           # Row maps → JSON (stdout or file)
│   └── resources/
│       └── application.properties        # spring.main.web-application-type=none
└── src/test/java/com/nimrod/
    └── ...                               # Unit tests
```

## CLI Interface

```bash
# Basic usage
java -jar nimrod.jar decode \
    --schema schemas/game_event.fbs \
    --csv export.csv \
    --column payload

# With options
java -jar nimrod.jar decode \
    --schema schemas/game_event.fbs \
    --csv export.csv \
    --column payload \
    --encoding base64 \
    --pretty \
    --output decoded.json
```

### Arguments

| Argument       | Required | Default  | Description                                      |
|----------------|----------|----------|--------------------------------------------------|
| `--schema`     | Yes      | —        | Path to `.fbs` FlatBuffers schema file            |
| `--csv`        | Yes      | —        | Path to CSV export file                           |
| `--column`     | Yes      | —        | Column name(s) containing FlatBuffer blobs        |
| `--encoding`   | No       | base64   | Encoding of binary data in CSV (base64 / hex)     |
| `--pretty`     | No       | false    | Pretty-print JSON output                          |
| `--output`     | No       | stdout   | Output file path (default: print to console)      |
| `--root-type`  | No       | auto     | Root table type in schema (auto-detect if single) |

## Implementation Phases

### Phase 1 — Project Skeleton
- Spring Boot console app, Gradle build, fat JAR
- Picocli wired up with `decode` subcommand
- Arg parsing and validation

### Phase 2 — CSV Ingestion
- Read CSV with Apache Commons CSV
- Identify target column(s) by name
- Decode Base64/hex binary data from string cells
- Pass through non-binary columns as-is

### Phase 3 — FlatBuffer Decoding
- Parse `.fbs` schema file at runtime
- Use FlatBuffers reflection API to walk the binary buffer
- Produce `Map<String, Object>` per decoded buffer
- Handle nested tables, vectors, unions, enums

### Phase 4 — JSON Output
- Replace binary column value with decoded map
- Serialize full row as JSON via Jackson
- Support pretty-print and NDJSON modes
- Write to stdout or file

### Phase 5 — Polish
- Error handling and user-friendly messages
- Progress indicator for large CSVs
- Unit and integration tests
- README with usage examples

## Key Design Decisions

### Schema handling: Runtime reflection (chosen)
We use FlatBuffers reflection/schema parsing rather than compiled Java classes. This means:
- **Pro**: One JAR works with any schema — no rebuild needed per game/table
- **Con**: Slightly more complex decoding logic
- **Alternative**: If reflection proves too painful, fall back to requiring codegen and a classpath of generated classes

### Binary encoding in CSV
DBeaver typically exports binary columns as Base64 or hex. We default to Base64 but allow override via `--encoding`. Auto-detection is a stretch goal.

### Why Spring Boot for a CLI?
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
