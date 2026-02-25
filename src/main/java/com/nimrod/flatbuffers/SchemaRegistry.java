package com.nimrod.flatbuffers;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Scans a directory of .fbs schema files and builds a lookup map from
 * file_identifier (4-byte string) to schema metadata.
 *
 * <p>Example: a schema containing {@code file_identifier "FBSR";} will be
 * registered under key "FBSR", so when a FlatBuffer blob with that identifier
 * is encountered at decode time, this registry returns the matching schema.</p>
 */
@Component
public class SchemaRegistry {

    /**
     * Metadata about a single .fbs schema file.
     */
    public record SchemaEntry(
        String fileIdentifier,
        String rootType,
        Path filePath
    ) {}

    // TODO: Phase 1 — implement scanning and parsing

    /**
     * Scan all .fbs files in the given directory and register them by file_identifier.
     */
    public void loadFromDirectory(File schemaDir) throws IOException {
        // Walk directory, parse each .fbs for file_identifier and root_type directives
    }

    /**
     * Look up a schema by the 4-byte file_identifier extracted from a FlatBuffer blob.
     *
     * @return the matching SchemaEntry, or null if not found
     */
    public SchemaEntry findByIdentifier(String identifier) {
        return null;
    }

    /**
     * @return all registered schemas, keyed by file_identifier
     */
    public Map<String, SchemaEntry> getAllSchemas() {
        return Map.of();
    }

    /**
     * Print a formatted table of all known schemas — used by the "schemas" subcommand
     * and in error messages when an identifier is not recognised.
     */
    public String formatSchemaList() {
        // TODO: format as table: Identifier | Root Type | Schema File
        return "";
    }
}
