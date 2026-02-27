package com.nimrod.cli;

import com.nimrod.csv.CsvReader;
import com.nimrod.csv.CsvReader.CsvRow;
import com.nimrod.flatbuffers.FbDecoder;
import com.nimrod.output.JsonWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Component
@Command(
    name = "nimrod",
    mixinStandardHelpOptions = true,
    version = "nimrod 0.1.0",
    description = "Decode FlatBuffer-serialised columns from a CSV export to JSON. "
                + "Schemas are auto-matched via the 4-byte file_identifier in each buffer.",
    subcommands = {DecodeCommand.class, SchemasCommand.class}
)
public class NimrodCommand implements Callable<Integer>, CommandLineRunner, ExitCodeGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(NimrodCommand.class);

    public enum Encoding { base64, hex, raw }

    public enum Format { pretty, compact, ndjson }

    @Option(names = {"--csv", "-c"},
            description = "Path to the CSV export file.")
    private File csv;

    @Option(names = {"--column"},
            description = "Column name(s) containing FlatBuffer blobs. "
                        + "Omit to auto-detect binary columns.")
    private String[] columns;

    @Option(names = {"--encoding", "-e"}, defaultValue = "base64",
            description = "Encoding of binary data in CSV: base64, hex, or raw. Default: ${DEFAULT-VALUE}")
    private Encoding encoding;

    @Option(names = {"--format", "-f"}, defaultValue = "pretty",
            description = "Output format: pretty (default), compact, or ndjson.")
    private Format format;

    @Option(names = {"--output", "-o"},
            description = "Output file path. Default: stdout.")
    private File output;

    private final IFactory factory;
    private final CsvReader csvReader;
    private final FbDecoder fbDecoder;
    private final JsonWriter jsonWriter;
    private int exitCode;

    public NimrodCommand(IFactory factory, CsvReader csvReader, FbDecoder fbDecoder, JsonWriter jsonWriter) {
        this.factory = factory;
        this.csvReader = csvReader;
        this.fbDecoder = fbDecoder;
        this.jsonWriter = jsonWriter;
    }

    private static final Set<String> SUBCOMMANDS = Set.of("decode", "schemas");

    @Override
    public void run(String... args) {
        CommandLine cmd = new CommandLine(this, factory);
        if (shouldDefaultToDecode(args)) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "decode";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            exitCode = cmd.execute(newArgs);
        } else {
            exitCode = cmd.execute(args);
        }
    }

    /**
     * Detect when the user passes a raw value without the "decode" subcommand,
     * e.g. {@code nimrod "0xABC..." -e hex} instead of {@code nimrod decode "0xABC..." -e hex}.
     */
    private static boolean shouldDefaultToDecode(String[] args) {
        if (args.length == 0) return false;

        boolean hasPositionalValue = false;
        for (String arg : args) {
            if (SUBCOMMANDS.contains(arg)) return false;
            if ("--csv".equals(arg) || "-c".equals(arg)) return false;
            if ("--help".equals(arg) || "-h".equals(arg)) return false;
            if ("--version".equals(arg) || "-V".equals(arg)) return false;
            if (!arg.startsWith("-")) hasPositionalValue = true;
        }
        return hasPositionalValue;
    }

    @Override
    public Integer call() {
        if (csv == null) {
            new CommandLine(this).usage(System.out);
            return 0;
        }
        if (!csv.exists()) {
            System.err.println("Error: CSV file not found: " + csv.getPath());
            return 1;
        }

        try {
            List<CsvRow> csvRows = csvReader.read(csv, columns, encoding);
            if (csvRows.isEmpty()) {
                System.err.println("No rows found in CSV.");
                return 0;
            }

            int totalRows = csvRows.size();
            boolean showProgress = totalRows >= 100;
            if (showProgress) {
                System.err.printf("Decoding %,d rows...%n", totalRows);
            }

            List<Map<String, Object>> decodedRows = new ArrayList<>();
            int errorCount = 0;

            for (int i = 0; i < totalRows; i++) {
                CsvRow row = csvRows.get(i);
                Map<String, Object> decodedRow = new LinkedHashMap<>(row.stringColumns());

                for (Map.Entry<String, ByteBuffer> entry : row.binaryColumns().entrySet()) {
                    try {
                        Map<String, Object> decoded = fbDecoder.decode(entry.getValue());
                        decodedRow.put(entry.getKey(), decoded);
                    } catch (Exception e) {
                        LOG.warn("Row {}: failed to decode column '{}': {}",
                                i + 1, entry.getKey(), e.getMessage());
                        decodedRow.put(entry.getKey(), "<decode error: " + e.getMessage() + ">");
                        errorCount++;
                    }
                }

                decodedRows.add(decodedRow);

                if (showProgress && (i + 1) % 1000 == 0) {
                    System.err.printf("  %,d / %,d rows decoded...%n", i + 1, totalRows);
                }
            }

            if (showProgress) {
                System.err.printf("  %,d / %,d rows decoded.%n", totalRows, totalRows);
            }

            jsonWriter.write(decodedRows, format, output);

            if (errorCount > 0) {
                System.err.printf("%d decode error(s) encountered. See log for details.%n", errorCount);
            }

            return 0;

        } catch (java.io.FileNotFoundException e) {
            System.err.println("Error: could not open output file: " + e.getMessage());
            return 1;
        } catch (java.io.IOException e) {
            System.err.println("Error reading CSV: " + e.getMessage());
            LOG.error("CSV read failed", e);
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            LOG.error("Decode failed", e);
            return 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
