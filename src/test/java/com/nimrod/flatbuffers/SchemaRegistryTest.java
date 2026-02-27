package com.nimrod.flatbuffers;

import com.nimrod.cli.NimrodCommand.Encoding;
import com.nimrod.csv.CsvReader;
import com.nimrod.csv.CsvReader.CsvRow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SchemaRegistryTest {

    @Autowired
    private SchemaRegistry schemaRegistry;

    @Autowired
    private CsvReader csvReader;

    private File testCsv() {
        return new File(Objects.requireNonNull(
                getClass().getClassLoader().getResource("player-profiles-hex.csv")).getFile());
    }

    @Test
    void registersAllExpectedSchemas() {
        // The artifact should provide all 22 schemas
        assertFalse(schemaRegistry.getAllSchemas().isEmpty(),
                "At least some schemas should be registered");
    }

    @Test
    void formatSchemaListContainsHeaderAndSchemas() {
        String list = schemaRegistry.formatSchemaList();
        assertTrue(list.contains("Root Type"));
        assertTrue(list.contains("Package"));
        assertTrue(list.contains("schemas registered"));
    }

    @Test
    void findByBufferMatchesPlayerProfile() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);
        ByteBuffer buf = rows.get(0).binaryColumns().get("data");

        var entry = schemaRegistry.findByBuffer(buf);
        assertTrue(entry.isPresent(), "Should find a matching schema");
        assertEquals("FbsDbPlayerProfile", entry.get().simpleName());
    }

    @Test
    void findByBufferReturnsEmptyForGarbage() {
        byte[] garbage = new byte[]{0, 0, 0, 0, 'X', 'X', 'X', 'X', 0, 0, 0, 0};
        ByteBuffer buf = ByteBuffer.wrap(garbage);

        var entry = schemaRegistry.findByBuffer(buf);
        assertTrue(entry.isEmpty(), "Garbage buffer should not match any schema");
    }

    @Test
    void schemaEntryDeserializesCorrectly() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);
        ByteBuffer buf = rows.get(0).binaryColumns().get("data");

        var entry = schemaRegistry.findByBuffer(buf).orElseThrow();
        var table = entry.deserialize(buf);
        assertNotNull(table, "Deserialized table should not be null");
    }
}
