package com.github.semanticgit.cli.command;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.core.StatisticsProvider;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
        name = "overview",
        description = "Show the overview of the repository"
)
public class OverviewCommand implements Runnable {
    @CommandLine.ParentCommand
    StatisticsCommand parent;

    @Override
    public void run() {
        if (!parent.dbFilePath.toFile().exists()) {
            System.err.println("Database file not found: " + parent.dbFilePath);
            return;
        }

        try (DatabaseManager dbManager = new DatabaseManager(parent.dbFilePath.toFile())) {
            StatisticsProvider provider = new StatisticsProvider(dbManager);
            SimpleEntityChangeStatistics stats = provider.getSimpleEntityChangeStatistics();

            if (!stats.isSuccess()) {
                System.err.println("Failed to read statistics");
                return;
            }

            System.out.println("=== Repository Overview ===");
            System.out.println("Database:    " + parent.dbFilePath);
            System.out.println("Total commits:  " + stats.getTotalCommits());
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
