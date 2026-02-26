package com.nimrod.cli;

import com.nimrod.flatbuffers.SchemaRegistry;

import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Component
@Command(
    name = "schemas",
    mixinStandardHelpOptions = true,
    description = "List all known FlatBuffer schemas from the sup-server-db-fbs-schema artifact."
)
public class SchemasCommand implements Callable<Integer> {

    private final SchemaRegistry schemaRegistry;

    public SchemasCommand(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public Integer call() {
        System.out.println(schemaRegistry.formatSchemaList());
        return 0;
    }
}
