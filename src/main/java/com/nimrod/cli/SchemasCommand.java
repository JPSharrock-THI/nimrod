package com.nimrod.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Component
@Command(
    name = "schemas",
    mixinStandardHelpOptions = true,
    description = "List all known FlatBuffer schemas and their file_identifiers."
)
public class SchemasCommand implements Callable<Integer> {

    @Option(names = {"--schema-dir"},
            description = "Path to directory of .fbs schema files. "
                        + "Default: bundled schemas/ submodule.")
    private File schemaDir;

    @Override
    public Integer call() {
        // TODO: load SchemaRegistry, print formatted table
        // Identifier | Root Type         | Schema File
        // FBSR       | FbsDbResearchState | research_state.fbs
        // FBSA       | FbsDbArmy          | army.fbs
        System.out.println("Listing schemas from: " + (schemaDir != null ? schemaDir.getPath() : "bundled"));
        return 0;
    }
}
