package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.CommitEntityChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * @see com.github.semanticgit.core.AbstractStatisticsProvider
 */
@Slf4j
public class StatisticsProvider extends AbstractStatisticsProvider {
    private final DatabaseManager dbManager;

    public StatisticsProvider(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public SimpleEntityChangeStatistics getSimpleEntityChangeStatistics() {
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);

        int totalCommits = countCommits();
        log.info("Total commits from commit_meta: {}", totalCommits);

        try(ResultSet rs = changeLogDao.querySimpleEntityChangeStatistics()) {
            Map<ChangeOperation, Float> operationFloatMap = new EnumMap<>(ChangeOperation.class);
            Map<ChangeNatureFlag, Float> natureFlagFloatMap = new EnumMap<>(ChangeNatureFlag.class);

            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
                int code = rs.getInt("value");
                float opRatio = rs.getFloat("operation_ratio");
                float nfRatio = rs.getFloat("nature_flag_ratio");

                log.info("Row {}: code={}, opRatio={}, nfRatio={}", rowCount, code, opRatio, nfRatio);

                if (opRatio > 0 && ChangeOperation.hasCode(code)) {
                    ChangeOperation op = ChangeOperation.fromCode(code);
                    operationFloatMap.put(op, opRatio);
                }

                if (nfRatio > 0) {
                    EnumSet<ChangeNatureFlag> nf = ChangeNatureFlag.fromCode(code);
                    for (ChangeNatureFlag flag : nf) {
                        natureFlagFloatMap.merge(flag, nfRatio, Float::sum);
                    }
                }
            }

            log.info("Query returned {} rows", rowCount);
            log.info("operationFloatMap: {}", operationFloatMap);
            log.info("natureFlagFloatMap: {}", natureFlagFloatMap);

            return SimpleEntityChangeStatistics.builder()
                    .totalCommits(totalCommits)
                    .operationFloatMap(operationFloatMap)
                    .natureFlagFloatMap(natureFlagFloatMap)
                    .build();

        } catch (SQLException e) {
            log.error("Failed to get simple entity change statistics", e);
            return SimpleEntityChangeStatistics.fail();
        }
    }

    @Override
    public CommitEntityChangeStatistics getCommitEntityChangeStatistics(String hash) {
        CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);

        try {
            var commitMeta = commitMetaDao.findByHash(hash);
            if (commitMeta == null) {
                log.warn("Commit not found: {}", hash);
                return null;
            }

            try (ResultSet rs = changeLogDao.queryCommitEntityChangeStatistics(hash)) {
                Map<ChangeOperation, Float> operationFloatMap = new EnumMap<>(ChangeOperation.class);
                Map<ChangeNatureFlag, Float> natureFlagFloatMap = new EnumMap<>(ChangeNatureFlag.class);

                while (rs.next()) {
                    int code = rs.getInt("value");
                    float opRatio = rs.getFloat("operation_ratio");
                    float nfRatio = rs.getFloat("nature_flag_ratio");

                    if (opRatio > 0 && ChangeOperation.hasCode(code)) {
                        ChangeOperation op = ChangeOperation.fromCode(code);
                        operationFloatMap.put(op, opRatio);
                    }

                    if (nfRatio > 0) {
                        EnumSet<ChangeNatureFlag> nf = ChangeNatureFlag.fromCode(code);
                        for (ChangeNatureFlag flag : nf) {
                            natureFlagFloatMap.merge(flag, nfRatio, Float::sum);
                        }
                    }
                }

                return CommitEntityChangeStatistics.builder()
                        .commitMeta(commitMeta)
                        .operationFloatMap(operationFloatMap)
                        .natureFlagFloatMap(natureFlagFloatMap)
                        .build();
            }
        } catch (SQLException e) {
            log.error("Failed to get commit entity change statistics for hash: {}", hash, e);
            return null;
        }
    }

    @Override
    public EntityChangeHistory getEntityChangeHistory(String entityName, String refName) {
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);
        return changeLogDao.queryEntityChangeHistory(entityName, refName);
    }

    @Override
    public AuthorChangeStatistics getAuthorChangeStatistics(Author author, @Nullable String refName) {
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);
        return changeLogDao.queryAuthorChangeStatistics(author, refName);
    }

    @Override
    public List<Author> getAuthors() {
        CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
        return commitMetaDao.findAllAuthors();
    }

    @Override
    public List<Entity> getEntities() {
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);
        return changeLogDao.findAllEntities();
    }

    @Override
    public List<CommitMeta> getCommits() {
        CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
        return commitMetaDao.findAll();
    }

    private int countCommits() {
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM commit_meta")) {
            if (rs.next()) {
                int count = rs.getInt(1);
                log.info("countCommits result: {}", count);
                return count;
            }
        } catch (SQLException e) {
            log.error("Failed to count commits", e);
        }
        return 0;
    }
}
