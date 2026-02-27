package com.nimrod.csv;

import com.nimrod.cli.NimrodCommand.Encoding;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads a CSV file exported from DBeaver/GDB and extracts rows.
 * Binary columns are decoded from Base64/hex and returned as {@link ByteBuffer}s.
 * Non-binary columns are passed through as strings.
 */
@Component
public class CsvReader {

    private static final Logger LOG = LoggerFactory.getLogger(CsvReader.class);

    /**
     * A single row from the CSV, with binary columns decoded into ByteBuffers
     * and everything else kept as strings.
     */
    public record CsvRow(
            Map<String, String> stringColumns,
            Map<String, ByteBuffer> binaryColumns
    ) {}

    /**
     * Read the CSV file and return all rows.
     *
     * @param csvFile      the CSV file to read
     * @param targetColumns explicit column names to treat as binary, or null for auto-detect
     * @param encoding     how binary data is encoded in the CSV
     * @return list of parsed rows
     */
    public List<CsvRow> read(File csvFile, String[] targetColumns, Encoding encoding)
            throws IOException {

        List<CsvRow> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvFile.toPath(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty()) {
                LOG.warn("CSV file has no headers");
                return rows;
            }
            LOG.info("CSV headers: {}", headers);

            // Determine which columns to treat as binary
            List<String> binaryColumnNames;
            if (targetColumns != null && targetColumns.length > 0) {
                // Validate that requested columns exist
                List<String> missing = new ArrayList<>();
                for (String col : targetColumns) {
                    if (!headers.contains(col)) {
                        missing.add(col);
                    }
                }
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Column(s) not found in CSV: " + missing
                                    + ". Available columns: " + headers);
                }
                binaryColumnNames = List.of(targetColumns);
                LOG.info("Targeting columns: {}", binaryColumnNames);
            } else {
                binaryColumnNames = new ArrayList<>();
                LOG.info("Auto-detect mode: will detect binary columns from first row");
            }

            boolean firstRow = true;
            for (CSVRecord record : parser) {
                // Auto-detect on first row if no explicit columns
                if (firstRow && binaryColumnNames.isEmpty()) {
                    binaryColumnNames = autoDetectBinaryColumns(record, headers, encoding);
                    if (binaryColumnNames.isEmpty()) {
                        LOG.warn("No binary columns detected. "
                                + "Try specifying --column explicitly or check --encoding.");
                    } else {
                        LOG.info("Auto-detected binary columns: {}", binaryColumnNames);
                    }
                    firstRow = false;
                }

                Map<String, String> stringCols = new LinkedHashMap<>();
                Map<String, ByteBuffer> binaryCols = new LinkedHashMap<>();

                for (String header : headers) {
                    String value = record.get(header);
                    if (binaryColumnNames.contains(header) && value != null && !value.isBlank()) {
                        try {
                            byte[] decoded = decodeBinary(value, encoding);
                            byte[] decompressed = tryDecompress(decoded);
                            binaryCols.put(header, ByteBuffer.wrap(decompressed));
                        } catch (Exception e) {
                            LOG.warn("Failed to decode column '{}' in row {}: {}",
                                    header, record.getRecordNumber(), e.getMessage());
                            stringCols.put(header, value);
                        }
                    } else {
                        stringCols.put(header, value);
                    }
                }

                rows.add(new CsvRow(stringCols, binaryCols));
            }
        }

        LOG.info("Read {} rows from {}", rows.size(), csvFile.getName());
        return rows;
    }

    /**
     * Auto-detect binary columns by trying to decode each column value
     * and checking if it looks like valid FlatBuffer data (at least 8 bytes).
     */
    private List<String> autoDetectBinaryColumns(
            CSVRecord record, List<String> headers, Encoding encoding) {

        List<String> candidates = new ArrayList<>();
        for (String header : headers) {
            String value = record.get(header);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                byte[] decoded = decodeBinary(value, encoding);
                byte[] decompressed = tryDecompress(decoded);
                // A valid FlatBuffer needs at least 8 bytes (4 root offset + 4 identifier)
                if (decompressed.length >= 8) {
                    candidates.add(header);
                }
            } catch (Exception e) {
                // Not binary data — skip
            }
        }
        return candidates;
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

    /** Try to gzip-decompress the data. If it's not gzipped, return the original bytes. */
    private byte[] tryDecompress(byte[] data) {
        if (data.length < 2) {
            return data;
        }
        // Check gzip magic: 0x1f 0x8b
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
