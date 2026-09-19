package com.github.semanticgit.cli.command;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.core.StatisticsProvider;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.CommitEntityChangeStatistics;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Map;

@CommandLine.Command(
        name = "commit",
        description = "Show statistics for a specific commit"
)
public class CommitCommand implements Runnable {
    @CommandLine.ParentCommand
    StatisticsCommand parent;

    @CommandLine.Parameters(
            index = "0",
            description = "Commit hash"
    )
    String hash;

    @Override
    public void run() {
        Path targetPath = parent.targetPath;
        if (targetPath.toFile().isDirectory()) {
            String dbName = Integer.toHexString(targetPath.toAbsolutePath().hashCode());
            targetPath = parent.dbDir.resolve(dbName + ".db");
        }
        if (!targetPath.toFile().exists()) {
            System.err.println("Database file not found: " + targetPath);
            return;
        }

        try (DatabaseManager dbManager = new DatabaseManager(targetPath.toFile())) {
            StatisticsProvider provider = new StatisticsProvider(dbManager);
            CommitEntityChangeStatistics stats = provider.getCommitEntityChangeStatistics(hash);

            if (stats == null) {
                System.err.println("Commit not found: " + hash);
                return;
            }

            System.out.println("=== Commit Statistics ===");
            System.out.println("Hash:       " + stats.getCommitMeta().getHash());
            System.out.println("Author:     " + stats.getCommitMeta().getAuthor().getName()
                    + " <" + stats.getCommitMeta().getAuthor().getEmail() + ">");
            System.out.println("Timestamp:  " + stats.getCommitMeta().getTimestamp());
            System.out.println("Message:    " + stats.getCommitMeta().getMessage());
            System.out.println();

            printOperationDistribution(stats.getOperationFloatMap());
            System.out.println();
            printNatureDistribution(stats.getNatureFlagFloatMap());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void printOperationDistribution(Map<ChangeOperation, Float> map) {
        System.out.println("--- Operation Type Distribution ---");
        for (ChangeOperation op : ChangeOperation.values()) {
            Float ratio = map.getOrDefault(op, 0f);
            int filled = Math.round(ratio * 20);
            String bar = "#".repeat(filled) + ".".repeat(20 - filled);
            System.out.printf("  %-8s %-20s %.1f%%%n", op.desc, bar, ratio * 100);
        }
    }

    private void printNatureDistribution(Map<ChangeNatureFlag, Float> map) {
        System.out.println("--- Change Nature Distribution ---");
        for (ChangeNatureFlag flag : ChangeNatureFlag.values()) {
            Float ratio = map.getOrDefault(flag, 0f);
            int filled = Math.round(ratio * 20);
            String bar = "#".repeat(filled) + ".".repeat(20 - filled);
            System.out.printf("  %-8s %-20s %.1f%%%n", flag.name(), bar, ratio * 100);
        }
    }
}
