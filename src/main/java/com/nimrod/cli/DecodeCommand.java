package com.nimrod.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Component
@Command(
    name = "decode",
    mixinStandardHelpOptions = true,
    version = "nimrod 0.1.0",
    description = "Decode FlatBuffer-serialised columns from a CSV export to JSON. "
                + "Schemas are auto-matched via the 4-byte file_identifier in each buffer."
)
public class DecodeCommand implements Callable<Integer> {

    public enum Encoding { base64, hex, raw }

    public enum Format { pretty, compact, ndjson }

    @Option(names = {"--csv", "-c"}, required = true,
            description = "Path to the CSV export file.")
    private File csv;

    @Option(names = {"--schema-dir"},
            description = "Path to directory of .fbs schema files. "
                        + "Default: bundled schemas/ submodule.")
    private File schemaDir;

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

    @Override
    public Integer call() {
        // TODO: wire up SchemaRegistry → CsvReader → FbDecoder → JsonWriter pipeline
        System.out.println("CSV:        " + csv.getPath());
        System.out.println("Schema dir: " + (schemaDir != null ? schemaDir.getPath() : "bundled"));
        System.out.println("Encoding:   " + encoding);
        System.out.println("Format:     " + format);

        if (columns != null && columns.length > 0) {
            System.out.println("Columns:    " + String.join(", ", columns));
        } else {
            System.out.println("Columns:    auto-detect");
        }

        if (output != null) {
            System.out.println("Output:     " + output.getPath());
        } else {
            System.out.println("Output:     stdout");
        }

        return 0;
    }
}
