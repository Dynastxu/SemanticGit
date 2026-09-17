package com.github.semanticgit.cli;

import com.github.semanticgit.cli.command.AnalyzeCommand;
import com.github.semanticgit.cli.command.StatisticsCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "semgit",
        mixinStandardHelpOptions = true,
        version = "0.0.0",
        subcommands = {
                AnalyzeCommand.class,
                StatisticsCommand.class
        }
)
public class Main implements Runnable {
    static void main(String[] args) {
        int exitCode = new CommandLine(Main.class).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
