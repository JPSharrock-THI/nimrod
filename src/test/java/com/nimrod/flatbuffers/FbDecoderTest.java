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
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FbDecoderTest {

    @Autowired
    private FbDecoder fbDecoder;

    @Autowired
    private CsvReader csvReader;

    private File testCsv() {
        return new File(Objects.requireNonNull(
                getClass().getClassLoader().getResource("player-profiles-hex.csv")).getFile());
    }

    @Test
    void decodeAddsTypeField() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);
        Map<String, Object> decoded = fbDecoder.decode(rows.get(0).binaryColumns().get("data"));

        assertTrue(decoded.containsKey("_type"), "Decoded map should have _type field");
        assertEquals("FbsDbPlayerProfile", decoded.get("_type"));
    }

    @Test
    void decodeContainsExpectedFields() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);
        // Row 1 has Governor title and faction 3
        Map<String, Object> decoded = fbDecoder.decode(rows.get(1).binaryColumns().get("data"));

        assertTrue(decoded.containsKey("title"));
        assertTrue(decoded.containsKey("faction"));
        assertTrue(decoded.containsKey("computerPlayer"));
    }

    @Test
    void throwsOnUnmatchedBuffer() {
        byte[] garbage = new byte[]{0, 0, 0, 0, 'Z', 'Z', 'Z', 'Z', 0, 0, 0, 0};
        ByteBuffer buf = ByteBuffer.wrap(garbage);

        assertThrows(IllegalArgumentException.class, () -> fbDecoder.decode(buf));
    }

    @Test
    void throwsWithSchemaListOnUnmatched() {
        byte[] garbage = new byte[]{0, 0, 0, 0, 'Z', 'Z', 'Z', 'Z', 0, 0, 0, 0};
        ByteBuffer buf = ByteBuffer.wrap(garbage);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> fbDecoder.decode(buf));
        assertTrue(ex.getMessage().contains("No matching FBS schema"),
                "Error should explain the problem");
    }

    @Test
    void decodesAllRowsWithoutException() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);
        for (int i = 0; i < rows.size(); i++) {
            ByteBuffer buf = rows.get(i).binaryColumns().get("data");
            Map<String, Object> decoded = assertDoesNotThrow(
                    () -> fbDecoder.decode(buf),
                    "Row " + i + " should decode without error");
            assertNotNull(decoded);
            assertFalse(decoded.isEmpty());
        }
    }
}
