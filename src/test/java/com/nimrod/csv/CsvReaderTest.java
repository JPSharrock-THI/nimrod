package com.nimrod.csv;

import com.nimrod.cli.NimrodCommand.Encoding;
import com.nimrod.csv.CsvReader.CsvRow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CsvReaderTest {

    @Autowired
    private CsvReader csvReader;

    private File resource(String name) {
        return new File(Objects.requireNonNull(
                getClass().getClassLoader().getResource(name)).getFile());
    }

    @Test
    void readsHexEncodedCsv() throws Exception {
        List<CsvRow> rows = csvReader.read(
                resource("player-profiles-hex.csv"), new String[]{"data"}, Encoding.hex);
        assertEquals(7, rows.size());
        for (CsvRow row : rows) {
            assertTrue(row.binaryColumns().containsKey("data"));
            assertTrue(row.binaryColumns().get("data").remaining() >= 8,
                    "FlatBuffer must be at least 8 bytes");
        }
    }

    @Test
    void readsBase64EncodedCsv() throws Exception {
        List<CsvRow> rows = csvReader.read(
                resource("player-profiles-base64.csv"), new String[]{"data"}, Encoding.base64);
        assertEquals(2, rows.size());
        for (CsvRow row : rows) {
            assertTrue(row.binaryColumns().containsKey("data"));
        }
    }

    @Test
    void autoDetectsHexBinaryColumns() throws Exception {
        List<CsvRow> rows = csvReader.read(
                resource("player-profiles-hex.csv"), null, Encoding.hex);
        assertEquals(7, rows.size());
        // Auto-detect should find the "data" column
        for (CsvRow row : rows) {
            assertTrue(row.binaryColumns().containsKey("data"),
                    "Auto-detect should identify 'data' as binary");
        }
    }

    @Test
    void preservesStringColumns() throws Exception {
        List<CsvRow> rows = csvReader.read(
                resource("player-profiles-hex.csv"), new String[]{"data"}, Encoding.hex);
        // The "id" column should be a string column
        for (CsvRow row : rows) {
            assertTrue(row.stringColumns().containsKey("id"),
                    "Non-binary columns should be preserved as strings");
        }
    }

    @Test
    void stringOnlyCsvReturnsNoBindaryColumns() throws Exception {
        List<CsvRow> rows = csvReader.read(
                resource("simple-string-only.csv"), null, Encoding.base64);
        assertEquals(2, rows.size());
        for (CsvRow row : rows) {
            assertTrue(row.binaryColumns().isEmpty(),
                    "String-only CSV should have no binary columns");
            assertFalse(row.stringColumns().isEmpty());
        }
    }

    @Test
    void emptyCsvReturnsNoRows() throws Exception {
        List<CsvRow> rows = csvReader.read(
                resource("empty.csv"), null, Encoding.base64);
        assertTrue(rows.isEmpty());
    }

    @Test
    void throwsOnMissingColumn() {
        assertThrows(IllegalArgumentException.class, () ->
                csvReader.read(resource("player-profiles-hex.csv"),
                        new String[]{"nonexistent"}, Encoding.hex));
    }

    @Test
    void throwsOnMissingColumnWithHelpfulMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                csvReader.read(resource("player-profiles-hex.csv"),
                        new String[]{"nonexistent"}, Encoding.hex));
        assertTrue(ex.getMessage().contains("nonexistent"),
                "Error should mention the missing column name");
        assertTrue(ex.getMessage().contains("id") && ex.getMessage().contains("data"),
                "Error should list available columns");
    }
}
