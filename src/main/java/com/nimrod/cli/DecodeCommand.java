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
    description = "Decode FlatBuffer-serialised columns from a CSV export to JSON."
)
public class DecodeCommand implements Callable<Integer> {

    @Option(names = {"--schema", "-s"}, required = true,
            description = "Path to the .fbs FlatBuffers schema file.")
    private File schema;

    @Option(names = {"--csv", "-c"}, required = true,
            description = "Path to the CSV export file.")
    private File csv;

    @Option(names = {"--column"}, required = true,
            description = "Column name(s) containing FlatBuffer blobs.")
    private String[] columns;

    @Option(names = {"--encoding", "-e"}, defaultValue = "base64",
            description = "Encoding of binary data in CSV: base64 or hex. Default: ${DEFAULT-VALUE}")
    private String encoding;

    @Option(names = {"--pretty", "-p"},
            description = "Pretty-print JSON output.")
    private boolean pretty;

    @Option(names = {"--output", "-o"},
            description = "Output file path. Default: stdout.")
    private File output;

    @Option(names = {"--root-type"},
            description = "Root table type in the schema. Auto-detected if the schema has a single root type.")
    private String rootType;

    @Override
    public Integer call() {
        // TODO: Phase 1 — wire up CsvReader → FbDecoder → JsonWriter pipeline
        System.out.println("Decoding " + csv.getName() + " using schema " + schema.getName());
        System.out.println("Target columns: " + String.join(", ", columns));
        return 0;
    }
}
