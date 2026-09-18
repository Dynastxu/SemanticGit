package com.github.semanticgit.cli.command;

import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
        name = "stat",
        description = "Show statistics of the repository",
        mixinStandardHelpOptions = true,
        subcommands = {
                OverviewCommand.class,
                CommitCommand.class,
                EntityCommand.class
        }
)
public class StatisticsCommand implements Runnable {
    @CommandLine.Parameters(
            index = "0",
            descriptionKey = "stat.dbFilePath"
    )
    Path targetPath;

    @CommandLine.Option(
            names = {"-d", "--db-dir"},
            descriptionKey = "analyze.dbDir",
            defaultValue = "${user.home}/.semanticgit/db"
    )
    Path dbDir;

    @Override
    public void run() {

    }
}
