package com.nimrod;

import com.nimrod.cli.NimrodCommand.Encoding;
import com.nimrod.csv.CsvReader;
import com.nimrod.csv.CsvReader.CsvRow;
import com.nimrod.flatbuffers.FbDecoder;

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
class PlayerProfileDecodeTest {

    @Autowired
    private CsvReader csvReader;

    @Autowired
    private FbDecoder fbDecoder;

    private File testCsv() {
        return new File(Objects.requireNonNull(
                getClass().getClassLoader().getResource("player-profiles-hex.csv")).getFile());
    }

    @Test
    void readsCsvWithHexEncoding() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);

        assertEquals(7, rows.size());
        // Every row should have the "data" column decoded as binary
        for (CsvRow row : rows) {
            assertTrue(row.binaryColumns().containsKey("data"),
                    "Row " + row.stringColumns().get("id") + " missing binary 'data' column");
        }
    }

    @Test
    void decodesPlayerProfileFields() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);

        // Row 0: computer player with no title
        Map<String, Object> row0 = fbDecoder.decode(rows.get(0).binaryColumns().get("data"));
        assertEquals("FbsDbPlayerProfile", row0.get("_type"));
        assertEquals("", row0.get("title"));
        assertEquals(true, row0.get("computerPlayer"));

        // Row 1: Governor, faction 3
        Map<String, Object> row1 = fbDecoder.decode(rows.get(1).binaryColumns().get("data"));
        assertEquals("Governor", row1.get("title"));
        assertEquals(3, row1.get("faction"));

        // Row 2: Commander, faction 2
        Map<String, Object> row2 = fbDecoder.decode(rows.get(2).binaryColumns().get("data"));
        assertEquals("Commander", row2.get("title"));
        assertEquals(2, row2.get("faction"));

        // Row 3: Lieutenant Governor
        Map<String, Object> row3 = fbDecoder.decode(rows.get(3).binaryColumns().get("data"));
        assertEquals("Lieutenant Governor", row3.get("title"));
    }

    @Test
    void decodesExoticTitles() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);

        // Row 4 (id=59): President
        Map<String, Object> president = fbDecoder.decode(rows.get(4).binaryColumns().get("data"));
        assertEquals("President", president.get("title"));

        // Row 5 (id=31): Maharaja
        Map<String, Object> maharaja = fbDecoder.decode(rows.get(5).binaryColumns().get("data"));
        assertEquals("Maharaja", maharaja.get("title"));

        // Row 6 (id=40): Dalai Lama
        Map<String, Object> dalaiLama = fbDecoder.decode(rows.get(6).binaryColumns().get("data"));
        assertEquals("Dalai Lama", dalaiLama.get("title"));
    }

    @Test
    void allBuffersMatchPlayerProfileSchema() throws Exception {
        List<CsvRow> rows = csvReader.read(testCsv(), new String[]{"data"}, Encoding.hex);

        for (CsvRow row : rows) {
            ByteBuffer buf = row.binaryColumns().get("data");
            Map<String, Object> decoded = fbDecoder.decode(buf);
            assertEquals("FbsDbPlayerProfile", decoded.get("_type"),
                    "Row " + row.stringColumns().get("id") + " should be FbsDbPlayerProfile");
        }
    }
}
