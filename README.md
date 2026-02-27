# Nimrod — FlatBuffers CSV Decoder CLI

Decode FlatBuffer-serialised binary data as human-readable JSON. Works with
single values copied from the DB or bulk CSV exports from DBeaver.

```
# Single value — just paste it
nimrod "0xABC123..." -e hex

# Bulk CSV export
nimrod --csv export.csv
```

Schemas are sourced from `com.bytro.sup:sup-server-db-fbs-schema`. The tool
reads the 4-byte `file_identifier` from each FlatBuffer blob and automatically
matches it to the correct deserializer — no manual `--schema` flag needed.

## Quick Start

```bash
# Build the fat JAR
./gradlew bootJar

# Decode a single value (hex from DB)
java -jar build/libs/nimrod-0.1.0-SNAPSHOT.jar "0xABC123..." -e hex

# Decode a single value (base64 — default encoding)
java -jar build/libs/nimrod-0.1.0-SNAPSHOT.jar "RAAAAEZC..."

# Pipe from stdin
echo "RAAAAEZC..." | java -jar build/libs/nimrod-0.1.0-SNAPSHOT.jar decode

# Bulk decode a CSV export
java -jar build/libs/nimrod-0.1.0-SNAPSHOT.jar --csv export.csv

# List all known FlatBuffer schemas
java -jar build/libs/nimrod-0.1.0-SNAPSHOT.jar schemas
```

## Usage

### Decode a single value

The most common use case — copy a FlatBuffer blob from the database and decode
it directly, no CSV needed:

```bash
# Hex-encoded value (e.g. from DBeaver)
nimrod "0x08000000464253..." -e hex

# Base64-encoded value (default)
nimrod "RAAAAEZC..."

# Pipe from stdin
echo "RAAAAEZC..." | nimrod decode

# Compact JSON to a file
nimrod "0x08000000464253..." -e hex -f compact -o decoded.json
```

### Decode a CSV export

For bulk decoding of multiple rows exported from DBeaver:

```bash
# Simplest — auto-detects binary columns, pretty-prints JSON
nimrod --csv export.csv

# Target a specific column
nimrod --csv export.csv --column payload

# Hex-encoded binary data (default is base64)
nimrod --csv export.csv --encoding hex

# Compact JSON to a file
nimrod --csv export.csv --format compact --output decoded.json

# NDJSON piped to jq
nimrod --csv export.csv --format ndjson | jq '.payload'

# Multiple binary columns
nimrod --csv export.csv --column data --column payload
```

### Arguments

**Single value mode** (default when no `--csv`):

| Argument         | Required | Default  | Description                                        |
|------------------|----------|----------|----------------------------------------------------|
| `<value>`        | No       | stdin    | The encoded FlatBuffer value (positional arg)      |
| `--encoding, -e` | No       | base64   | Encoding: `base64`, `hex`, or `raw`                |
| `--format, -f`   | No       | pretty   | Output format: `pretty` or `compact`               |
| `--output, -o`   | No       | stdout   | Output file path                                   |

**CSV mode** (`--csv`):

| Argument         | Required | Default  | Description                                                    |
|------------------|----------|----------|----------------------------------------------------------------|
| `--csv, -c`      | Yes      | —        | Path to CSV export file                                        |
| `--column`       | No       | auto     | Column name(s) containing FlatBuffer blobs. Omit to auto-detect|
| `--encoding, -e` | No       | base64   | Encoding of binary data: `base64`, `hex`, or `raw`             |
| `--format, -f`   | No       | pretty   | Output format: `pretty`, `compact`, or `ndjson`                |
| `--output, -o`   | No       | stdout   | Output file path                                               |

### Subcommands

| Subcommand | Description                           |
|------------|---------------------------------------|
| `decode`   | Decode a single FlatBuffer value      |
| `schemas`  | List all known FlatBuffer schemas     |

## How It Works

1. **CSV parsing** — reads the CSV, decodes binary columns from base64/hex
2. **Auto-detection** — if `--column` is omitted, probes each column in the
   first row to find binary data (≥8 bytes after decoding)
3. **Schema matching** — reads the 4-byte file identifier from each FlatBuffer
   blob and matches it against the 22 registered schemas
4. **Reflection decoding** — walks the generated FlatBuffer Java classes via
   reflection to produce `Map<String, Object>` per row
5. **JSON output** — serialises via Jackson in the requested format

## Building

Requires Java 21.

```bash
./gradlew bootJar       # Fat JAR at build/libs/nimrod-*.jar
./gradlew test          # Run tests
```

## Project Structure

```
nimrod/
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── main/java/com/nimrod/
    │   ├── NimrodApplication.java          # Spring Boot entry point
    │   ├── cli/
    │   │   ├── NimrodCommand.java          # Main CLI command (picocli)
    │   │   ├── DecodeCommand.java          # 'decode' subcommand (single value)
    │   │   └── SchemasCommand.java         # 'schemas' subcommand
    │   ├── csv/
    │   │   └── CsvReader.java              # CSV parsing + binary detection
    │   ├── flatbuffers/
    │   │   ├── FbDecoder.java              # Reflection-based FlatBuffer decoder
    │   │   └── SchemaRegistry.java         # File-identifier → schema lookup
    │   └── output/
    │       └── JsonWriter.java             # JSON serialisation (pretty/compact/ndjson)
    └── test/java/com/nimrod/
        ├── PlayerProfileDecodeTest.java    # End-to-end decode tests
        ├── cli/DecodeCommandTest.java      # Single-value decode tests
        ├── csv/CsvReaderTest.java          # CSV reading + encoding tests
        ├── flatbuffers/
        │   ├── FbDecoderTest.java          # Decoder edge cases
        │   └── SchemaRegistryTest.java     # Schema lookup tests
        └── output/JsonWriterTest.java      # Output format tests
```

## Tech Stack

| Component   | Choice                        | Rationale                              |
|-------------|-------------------------------|----------------------------------------|
| Language    | Java 21                       | LTS, mature FlatBuffers support        |
| Framework   | Spring Boot 3.4 (no web)     | DI, familiar, picocli integration      |
| Build       | Gradle (Kotlin DSL)           | Fat JAR via bootJar                    |
| CLI parsing | Picocli                       | Rich CLI UX, subcommands, help text    |
| CSV         | Apache Commons CSV            | Battle-tested, handles edge cases      |
| FlatBuffers | google/flatbuffers + schemas  | Runtime FlatBuffer support             |
| JSON output | Jackson                       | Bundled with Spring Boot               |
