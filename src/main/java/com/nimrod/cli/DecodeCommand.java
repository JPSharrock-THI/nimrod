package com.nimrod.cli;

import com.nimrod.cli.NimrodCommand.Encoding;
import com.nimrod.cli.NimrodCommand.Format;
import com.nimrod.flatbuffers.FbDecoder;
import com.nimrod.output.JsonWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

/**
 * Decode a single FlatBuffer value without needing a CSV file.
 *
 * <p>Usage examples:
 * <pre>
 *   nimrod decode "SGVsbG8gV29ybGQ..."
 *   nimrod decode -e hex "0x1F8B..."
 *   echo "SGVsbG8..." | nimrod decode
 * </pre>
 */
@Component
@Command(
    name = "decode",
    mixinStandardHelpOptions = true,
    description = "Decode a single FlatBuffer value. "
                + "Pass the encoded value as an argument, or pipe via stdin."
)
public class DecodeCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(DecodeCommand.class);

    @Parameters(index = "0", arity = "0..1",
                description = "The encoded FlatBuffer value (base64/hex). Reads from stdin if omitted.")
    private String value;

    @Option(names = {"--encoding", "-e"}, defaultValue = "base64",
            description = "Encoding of the value: base64, hex, or raw. Default: ${DEFAULT-VALUE}")
    private Encoding encoding;

    @Option(names = {"--format", "-f"}, defaultValue = "pretty",
            description = "Output format: pretty (default) or compact.")
    private Format format;

    @Option(names = {"--output", "-o"},
            description = "Output file path. Default: stdout.")
    private File output;

    private final FbDecoder fbDecoder;
    private final JsonWriter jsonWriter;

    public DecodeCommand(FbDecoder fbDecoder, JsonWriter jsonWriter) {
        this.fbDecoder = fbDecoder;
        this.jsonWriter = jsonWriter;
    }

    @Override
    public Integer call() {
        String input = value;

        if (input == null || input.isBlank()) {
            try {
                input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                System.err.println("Error reading from stdin: " + e.getMessage());
                return 1;
            }
            if (input.isBlank()) {
                System.err.println("Error: no value provided. "
                        + "Pass as argument or pipe via stdin.");
                return 1;
            }
        }

        try {
            byte[] decoded = decodeBinary(input, encoding);
            byte[] decompressed = tryDecompress(decoded);
            ByteBuffer buffer = ByteBuffer.wrap(decompressed);

            Map<String, Object> result = fbDecoder.decode(buffer);
            jsonWriter.writeSingle(result, format, output);
            return 0;

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            LOG.error("Decode failed", e);
            return 1;
        }
    }

    private byte[] decodeBinary(String value, Encoding encoding) {
        return switch (encoding) {
            case base64 -> Base64.getDecoder().decode(value.strip());
            case hex -> {
                String hex = value.strip();
                if (hex.startsWith("0x") || hex.startsWith("0X")) {
                    hex = hex.substring(2);
                }
                yield HexFormat.of().parseHex(hex.replaceAll("[\\s-]", ""));
            }
            case raw -> value.getBytes(StandardCharsets.ISO_8859_1);
        };
    }

    private byte[] tryDecompress(byte[] data) {
        if (data.length < 2) {
            return data;
        }
        if ((data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            try (var gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
                return gis.readAllBytes();
            } catch (IOException e) {
                LOG.debug("Data looked like gzip but failed to decompress: {}", e.getMessage());
            }
        }
        return data;
    }
}
