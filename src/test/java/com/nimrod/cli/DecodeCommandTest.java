package com.nimrod.cli;

import com.nimrod.flatbuffers.FbDecoder;
import com.nimrod.output.JsonWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DecodeCommandTest {

    @Autowired
    private IFactory factory;

    /** A hex-encoded FbsDbPlayerProfile (Governor, faction 3). */
    private static final String HEX_GOVERNOR =
            "0x440000004642505000003A00240000000000000000000000000008000C00000006000700000000"
            + "00100014001800000000000000000000001C00000000000000000020003A000000000001019402"
            + "000018000000FFFFFFFFFFFFFFFFFFFFFFFF030000006823000008000000476F7665726E6F7200"
            + "000000";

    /** The same Governor value encoded as base64 (derived from the hex above). */
    private static final String BASE64_GOVERNOR = "RAAAAEZCUFAAADoAJAAAAAAAAAAAAAAAAAAIAAwAAAAGAAcAAAAAABAAFAAYAAAAAAAAAAAAAAAcAAAAAAAAAAAAIAA6AAAAAAABAZQCAAAYAAAA////////////////AwAAAGgjAAAIAAAAR292ZXJub3IAAAAA";

    @Test
    void decodesHexValueFromArgument(@TempDir Path tempDir) throws Exception {
        Path outFile = tempDir.resolve("out.json");
        int exitCode = new CommandLine(DecodeCommand.class, factory)
                .execute(HEX_GOVERNOR, "-e", "hex", "-o", outFile.toString());

        assertEquals(0, exitCode);
        String json = Files.readString(outFile);
        assertTrue(json.contains("\"_type\" : \"FbsDbPlayerProfile\""));
        assertTrue(json.contains("\"Governor\""));
    }

    @Test
    void decodesBase64ValueFromArgument(@TempDir Path tempDir) throws Exception {
        Path outFile = tempDir.resolve("out.json");
        int exitCode = new CommandLine(DecodeCommand.class, factory)
                .execute(BASE64_GOVERNOR, "-e", "base64", "-o", outFile.toString());

        assertEquals(0, exitCode);
        String json = Files.readString(outFile);
        assertTrue(json.contains("\"_type\" : \"FbsDbPlayerProfile\""));
        assertTrue(json.contains("\"Governor\""));
    }

    @Test
    void decodesValueFromStdin(@TempDir Path tempDir) throws Exception {
        Path outFile = tempDir.resolve("out.json");
        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(
                    BASE64_GOVERNOR.getBytes(StandardCharsets.UTF_8)));

            int exitCode = new CommandLine(DecodeCommand.class, factory)
                    .execute("-o", outFile.toString());

            assertEquals(0, exitCode);
            String json = Files.readString(outFile);
            assertTrue(json.contains("\"FbsDbPlayerProfile\""));
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void outputIsNotWrappedInArray(@TempDir Path tempDir) throws Exception {
        Path outFile = tempDir.resolve("out.json");
        new CommandLine(DecodeCommand.class, factory)
                .execute(HEX_GOVERNOR, "-e", "hex", "-f", "compact", "-o", outFile.toString());

        String json = Files.readString(outFile).strip();
        assertTrue(json.startsWith("{"), "Single value should not be wrapped in array");
        assertFalse(json.startsWith("["), "Single value should not be wrapped in array");
    }

    @Test
    void returnsErrorForInvalidData() {
        int exitCode = new CommandLine(DecodeCommand.class, factory)
                .execute("bm90YWZsYXRidWZmZXI=");  // "notaflatbuffer" in base64

        assertNotEquals(0, exitCode);
    }
}
