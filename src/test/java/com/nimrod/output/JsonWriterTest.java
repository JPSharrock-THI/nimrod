package com.nimrod.output;

import com.nimrod.cli.NimrodCommand.Format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JsonWriterTest {

    @Autowired
    private JsonWriter jsonWriter;

    private List<Map<String, Object>> sampleRows() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", "1");
        row1.put("name", "Alice");

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", "2");
        row2.put("name", "Bob");

        return List.of(row1, row2);
    }

    @Test
    void writesPrettyJsonToFile(@TempDir Path tempDir) throws Exception {
        File outFile = tempDir.resolve("out.json").toFile();
        jsonWriter.write(sampleRows(), Format.pretty, outFile);

        String content = Files.readString(outFile.toPath());
        assertTrue(content.contains("\"id\""));
        assertTrue(content.contains("\"Alice\""));
        assertTrue(content.contains("\n"), "Pretty JSON should be multi-line");
    }

    @Test
    void writesCompactJsonToFile(@TempDir Path tempDir) throws Exception {
        File outFile = tempDir.resolve("out.json").toFile();
        jsonWriter.write(sampleRows(), Format.compact, outFile);

        String content = Files.readString(outFile.toPath()).trim();
        // Compact JSON should be a single line (plus trailing newline)
        String[] lines = content.split("\n");
        assertEquals(1, lines.length, "Compact JSON should be a single line");
        assertTrue(content.startsWith("["));
        assertTrue(content.contains("Alice"));
    }

    @Test
    void writesNdjsonToFile(@TempDir Path tempDir) throws Exception {
        File outFile = tempDir.resolve("out.ndjson").toFile();
        jsonWriter.write(sampleRows(), Format.ndjson, outFile);

        List<String> lines = Files.readAllLines(outFile.toPath()).stream()
                .filter(l -> !l.isBlank()).toList();
        assertEquals(2, lines.size(), "NDJSON should have one line per row");
        assertTrue(lines.get(0).contains("Alice"));
        assertTrue(lines.get(1).contains("Bob"));
        // Each line should be valid JSON object, not wrapped in array
        assertTrue(lines.get(0).startsWith("{"));
        assertTrue(lines.get(1).startsWith("{"));
    }

    @Test
    void writesNestedObjectsCorrectly(@TempDir Path tempDir) throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("_type", "TestType");
        nested.put("value", 42);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("payload", nested);

        File outFile = tempDir.resolve("out.json").toFile();
        jsonWriter.write(List.of(row), Format.compact, outFile);

        String content = Files.readString(outFile.toPath());
        assertTrue(content.contains("\"_type\":\"TestType\""));
        assertTrue(content.contains("\"value\":42"));
    }

    @Test
    void writesEmptyListAsEmptyArray(@TempDir Path tempDir) throws Exception {
        File outFile = tempDir.resolve("out.json").toFile();
        jsonWriter.write(List.of(), Format.compact, outFile);

        String content = Files.readString(outFile.toPath()).trim();
        assertTrue(content.equals("[]") || content.equals("[ ]"),
                "Empty list should serialize as empty JSON array, got: " + content);
    }
}
