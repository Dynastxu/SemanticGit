package com.github.semanticgit.cli.command;

import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.core.StatisticsProvider;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@CommandLine.Command(
        name = "entity",
        description = "Show change history for a specific entity"
)
public class EntityCommand implements Runnable {
    private static final int DEFAULT_LIMIT = 10;

    @CommandLine.ParentCommand
    StatisticsCommand parent;

    @CommandLine.Parameters(
            index = "0",
            description = "Entity fully qualified name"
    )
    String entityName;

    @CommandLine.Option(
            names = {"-r", "--ref"},
            description = "Git ref name (e.g., refs/heads/main)",
            required = true
    )
    String ref;

    @CommandLine.Option(
            names = {"-a", "--all"},
            description = "Show all changes (default: latest 10)"
    )
    boolean showAll;

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
            EntityChangeHistory history = provider.getEntityChangeHistory(entityName, ref);

            List<ChangeLog> changes = history.getChanges();

            if (changes.isEmpty()) {
                System.out.println("No change history found for entity: " + entityName);
                return;
            }

            System.out.println("=== Entity Change History ===");
            System.out.println("Entity: " + entityName);
            System.out.println("Ref:    " + ref);

            String countInfo = showAll
                    ? "Changes: " + changes.size()
                    : "Changes: " + changes.size() + " (showing " + DEFAULT_LIMIT + " most recent)";
            System.out.println(countInfo);
            System.out.print("Nature: ");
            printNatureStats(changes);
            System.out.println();
            System.out.println();

            int start = showAll ? 0 : Math.max(0, changes.size() - DEFAULT_LIMIT);
            int displayIdx = 0;
            for (int i = start; i < changes.size(); i++) {
                displayIdx++;
                ChangeLog cl = changes.get(i);
                String shortHash = cl.getCommit().getHash().length() > 7
                        ? cl.getCommit().getHash().substring(0, 7)
                        : cl.getCommit().getHash();
                String parentInfo = cl.getParentEntity() != null
                        ? " ← " + cl.getParentEntity().getName()
                        : "";

                String natureStr = formatNatureFlags(cl);

                System.out.printf("[%d] %s  %s  %s (%s)%s%n",
                        displayIdx,
                        shortHash,
                        cl.getOperation().desc,
                        cl.getEntity().getName(),
                        cl.getEntity().getKind(),
                        parentInfo);
                if (!natureStr.isEmpty()) {
                    System.out.printf("    nature: %s%n", natureStr);
                }

                System.out.printf("    author: %s <%s>  %s%n",
                        cl.getCommit().getAuthor().getName(),
                        cl.getCommit().getAuthor().getEmail(),
                        cl.getCommit().getTimestamp());
                System.out.printf("    file:   %s%n", cl.getFilePath());
                System.out.printf("    msg:    %s%n", firstLine(cl.getCommit().getMessage()));
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void printNatureStats(List<ChangeLog> changes) {
        Map<ChangeNatureFlag, Integer> counts = new EnumMap<>(ChangeNatureFlag.class);
        for (ChangeLog cl : changes) {
            EnumSet<ChangeNatureFlag> flags = ChangeNatureFlag.fromCode(cl.getNatureFlagCode());
            for (ChangeNatureFlag flag : flags) {
                counts.merge(flag, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            System.out.print("-");
            return;
        }
        int total = changes.size();
        boolean first = true;
        for (ChangeNatureFlag flag : ChangeNatureFlag.values()) {
            Integer count = counts.get(flag);
            if (count != null) {
                if (!first) {
                    System.out.print("  ");
                }
                System.out.printf("%s %.0f%%", flag, count * 100.0 / total);
                first = false;
            }
        }
    }

    private String formatNatureFlags(ChangeLog cl) {
        EnumSet<ChangeNatureFlag> flags = ChangeNatureFlag.fromCode(cl.getNatureFlagCode());
        if (flags.isEmpty()) {
            return "";
        }
        return flags.stream()
                .map(Enum::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String firstLine(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        int idx = message.indexOf('\n');
        return idx > 0 ? message.substring(0, idx) : message;
    }
}
