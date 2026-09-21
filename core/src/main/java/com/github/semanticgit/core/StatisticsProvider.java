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
import com.github.semanticgit.core.dto.StatRow;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

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
        CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);

        int totalCommits = commitMetaDao.count();
        log.info("Total commits from commit_meta: {}", totalCommits);

        try {
            List<StatRow> rows = changeLogDao.querySimpleEntityChangeStatistics();
            StatMaps maps = parseStatRows(rows);

            log.info("Query returned {} rows", rows.size());
            log.info("operationFloatMap: {}", maps.operationFloatMap());
            log.info("natureFlagFloatMap: {}", maps.natureFlagFloatMap());

            return SimpleEntityChangeStatistics.builder()
                    .totalCommits(totalCommits)
                    .operationFloatMap(maps.operationFloatMap())
                    .natureFlagFloatMap(maps.natureFlagFloatMap())
                    .build();
        } catch (Exception e) {
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

            List<StatRow> rows = changeLogDao.queryCommitEntityChangeStatistics(hash);
            StatMaps maps = parseStatRows(rows);

            return CommitEntityChangeStatistics.builder()
                    .commitMeta(commitMeta)
                    .operationFloatMap(maps.operationFloatMap())
                    .natureFlagFloatMap(maps.natureFlagFloatMap())
                    .build();
        } catch (Exception e) {
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

    private record StatMaps(
        Map<ChangeOperation, Float> operationFloatMap,
        Map<ChangeNatureFlag, Float> natureFlagFloatMap
    ) {}

    private StatMaps parseStatRows(List<StatRow> rows) {
        Map<ChangeOperation, Float> operationFloatMap = new EnumMap<>(ChangeOperation.class);
        Map<ChangeNatureFlag, Float> natureFlagFloatMap = new EnumMap<>(ChangeNatureFlag.class);

        for (StatRow row : rows) {
            int code = row.value();
            float opRatio = row.operationRatio();
            float nfRatio = row.natureFlagRatio();

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

        return new StatMaps(operationFloatMap, natureFlagFloatMap);
    }
}
