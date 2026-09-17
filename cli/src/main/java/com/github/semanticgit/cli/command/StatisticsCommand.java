package com.github.semanticgit.cli.command;

import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
        name = "stat",
        description = "Show statistics of the repository",
        mixinStandardHelpOptions = true,
        subcommands = {
                OverviewCommand.class
        }
)
public class StatisticsCommand implements Runnable {
    @CommandLine.Parameters(
            index = "0",
            description = "The database file path"
    )
    Path dbFilePath;

    @Override
    public void run() {

    }
}
