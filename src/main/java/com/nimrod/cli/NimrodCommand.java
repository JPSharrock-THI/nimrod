package com.nimrod.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

@Component
@Command(
    name = "nimrod",
    mixinStandardHelpOptions = true,
    version = "nimrod 0.1.0",
    description = "Decode FlatBuffer-serialised database exports to JSON.",
    subcommands = {DecodeCommand.class, SchemasCommand.class}
)
public class NimrodCommand implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private int exitCode;

    public NimrodCommand(IFactory factory) {
        this.factory = factory;
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(this, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
