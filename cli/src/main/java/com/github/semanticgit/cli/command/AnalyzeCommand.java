package com.github.semanticgit.cli.command;

import com.github.semanticgit.core.AnalysisEngine;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
        name = "analyze",
        description = "Analyze the repository",
        mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Runnable {
    @CommandLine.Parameters(
            index = "0",
            descriptionKey = "analyze.repoPath"
    )
    Path repoPath;

    @CommandLine.Option(
            names = {"-d", "--db-dir"},
            descriptionKey = "analyze.dbDir",
            defaultValue = "${user.home}/.semanticgit/db"
    )
    Path dbDir;

    @CommandLine.Option(
            names = {"-n", "--db-name"},
            descriptionKey = "analyze.dbFileName"
    )
    String dbFileName;

    @CommandLine.Option(
            names = {"-o", "--overwrite"},
            descriptionKey = "analyze.overwrite",
            defaultValue = "false"
    )
    boolean overwrite;

    @CommandLine.Option(
            names = {"-f", "--full"},
            descriptionKey = "analyze.fullAnalysis",
            defaultValue = "false"
    )
    boolean fullAnalysis;

    @Override
    public void run() {
        // 与SLF4J日志输出冲突，设置为WARN
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");

        if (!Files.isDirectory(repoPath)) {
            System.err.println("Error: Repository path does not exist - " + repoPath);
            return;
        }

        String dbName = dbFileName != null
                ? dbFileName
                : Integer.toHexString(repoPath.toAbsolutePath().hashCode());
        Path dbFile = dbDir.resolve(dbName + ".db");

        if (!overwrite && Files.exists(dbFile)) {
            System.out.println("Database file exists at: " + dbFile);
            System.out.println("Use --overwrite to force re-analysis");
            return;
        }

        System.out.println("Repository: " + repoPath);
        System.out.println("Database file: " + dbFile);

        AnalysisEngine engine = new AnalysisEngine();
        // TODO 区分全分析和增量分析
        try {
            engine.fullAnalysisAsync(
                    repoPath.toString(),
                    dbDir.toString(),
                    dbName,
                    p -> System.out.printf("\rProgress: %.0f%%", p * 100),
                    e -> {
                        System.err.println("\nAnalysis failed: " + e.getMessage());
                        return null;
                    }
            ).join();

            System.out.println("\nAnalysis completed!");
        } catch (Exception e) {
            System.err.println("Analysis failed: " + e.getMessage());
        }
    }
}
