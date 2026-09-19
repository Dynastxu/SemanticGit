package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import com.github.semanticgit.core.dto.StatRow;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChangeLogDaoImpl implements ChangeLogDao {
    private final DatabaseManager dbManager;

    public ChangeLogDaoImpl(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void save(@NonNull ChangeLog changeLog) {
        try {
            dbManager.getJdbi().useTransaction(handle -> {
                Long commitId = resolveCommitId(handle, changeLog.getCommit().getHash());
                Long entityId = upsertEntity(handle, changeLog.getEntity());
                Long parentEntityId = changeLog.getParentEntity() != null
                        ? upsertEntity(handle, changeLog.getParentEntity())
                        : null;
                insertChangeLog(handle, commitId, entityId, changeLog, parentEntityId);
            });
        } catch (Exception e) {
            log.error("Failed to save change log for commit {}: {}", changeLog.getCommit().getHash(), e.getMessage());
        }
    }

    @Override
    public void saveAll(@NonNull List<ChangeLog> changeLogs) {
        if (changeLogs.isEmpty()) {
            return;
        }
        try {
            dbManager.getJdbi().useTransaction(handle -> {
                for (ChangeLog changeLog : changeLogs) {
                    Long commitId = resolveCommitId(handle, changeLog.getCommit().getHash());
                    Long entityId = upsertEntity(handle, changeLog.getEntity());
                    Long parentEntityId = changeLog.getParentEntity() != null
                            ? upsertEntity(handle, changeLog.getParentEntity())
                            : null;
                    insertChangeLog(handle, commitId, entityId, changeLog, parentEntityId);
                }
            });
            log.info("Saved {} change logs to database", changeLogs.size());
        } catch (Exception e) {
            log.error("Failed to save change logs batch", e);
        }
    }

    @Override
    public List<StatRow> querySimpleEntityChangeStatistics() {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("""
                             WITH commit_counts AS (
                                 SELECT
                                     commit_id,
                                     COUNT(*) AS cnt
                                 FROM change_log
                                 GROUP BY commit_id
                             ),
                             total_commits AS (
                                 SELECT COUNT(*) AS total
                                 FROM commit_counts
                             ),
                             weighted AS (
                                 SELECT
                                     cl.operation,
                                     COALESCE(cl.nature_flag, 0) AS nature_flag,
                                     1.0 / cc.cnt AS w
                                 FROM change_log cl
                                 JOIN commit_counts cc
                                     ON cc.commit_id = cl.commit_id
                             ),
                             op_stats AS (
                                 SELECT
                                     operation AS value,
                                     SUM(w) / (SELECT total FROM total_commits) AS operation_ratio
                                 FROM weighted
                                 GROUP BY operation
                             ),
                             nf_stats AS (
                                 SELECT
                                     nature_flag AS value,
                                     SUM(w) / (SELECT total FROM total_commits) AS nature_flag_ratio
                                 FROM weighted
                                 GROUP BY nature_flag
                             ),
                             all_values AS (
                                 SELECT value FROM op_stats
                                 UNION
                                 SELECT value FROM nf_stats
                             )
                             SELECT
                                 av.value,
                                 ROUND(COALESCE(op.operation_ratio, 0), 6) AS operation_ratio,
                                 ROUND(COALESCE(nf.nature_flag_ratio, 0), 6) AS nature_flag_ratio
                             FROM all_values av
                             LEFT JOIN op_stats op ON op.value = av.value
                             LEFT JOIN nf_stats nf ON nf.value = av.value
                             ORDER BY av.value
                             """)
                    .map((rs, ctx) -> new StatRow(
                        rs.getInt("value"),
                        rs.getFloat("operation_ratio"),
                        rs.getFloat("nature_flag_ratio")))
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to query simple entity change statistics", e);
            return List.of();
        }
    }

    @Override
    public List<StatRow> queryCommitEntityChangeStatistics(String hash) {
        byte[] hashBytes = HexFormat.of().parseHex(hash);
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("""
                             WITH total AS (
                                 SELECT COUNT(*) AS cnt
                                 FROM change_log cl
                                 JOIN commit_meta cm ON cl.commit_id = cm.id
                                 WHERE cm.hash = :hash
                             ),
                             op_stats AS (
                                 SELECT
                                     cl.operation AS value,
                                     CAST(COUNT(*) AS REAL) / (SELECT cnt FROM total) AS operation_ratio
                                 FROM change_log cl
                                 JOIN commit_meta cm ON cl.commit_id = cm.id
                                 WHERE cm.hash = :hash
                                 GROUP BY cl.operation
                             ),
                             nf_stats AS (
                                 SELECT
                                     COALESCE(cl.nature_flag, 0) AS value,
                                     CAST(COUNT(*) AS REAL) / (SELECT cnt FROM total) AS nature_flag_ratio
                                 FROM change_log cl
                                 JOIN commit_meta cm ON cl.commit_id = cm.id
                                 WHERE cm.hash = :hash
                                 GROUP BY cl.nature_flag
                             ),
                             all_values AS (
                                 SELECT value FROM op_stats
                                 UNION
                                 SELECT value FROM nf_stats
                             )
                             SELECT
                                 av.value,
                                 ROUND(COALESCE(op.operation_ratio, 0), 6) AS operation_ratio,
                                 ROUND(COALESCE(nf.nature_flag_ratio, 0), 6) AS nature_flag_ratio
                             FROM all_values av
                             LEFT JOIN op_stats op ON op.value = av.value
                             LEFT JOIN nf_stats nf ON nf.value = av.value
                             ORDER BY av.value
                             """)
                    .bind("hash", hashBytes)
                    .map((rs, ctx) -> new StatRow(
                        rs.getInt("value"),
                        rs.getFloat("operation_ratio"),
                        rs.getFloat("nature_flag_ratio")))
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to query commit entity change statistics for hash: {}", hash, e);
            return List.of();
        }
    }

    @Override
    public EntityChangeHistory queryEntityChangeHistory(String entityName, String refName) {
        List<ChangeLog> changes = new ArrayList<>();
        try {
            Long headCommitId = resolveRefCommitId(refName);
            if (headCommitId == null) {
                log.warn("Ref not found: {}", refName);
                return EntityChangeHistory.builder().changes(changes).build();
            }

            debugEntityInfo(entityName);
            debugCommitParentStats();
            debugChainCount(entityName, headCommitId);

            dbManager.getJdbi().useHandle(handle -> {
                handle.createQuery("""
                            WITH RECURSIVE commit_chain AS (
                                SELECT cm.id, cm.parent_commit_id, cm.hash, cm.timestamp, cm.message,
                                       a.name AS author_name, a.email AS author_email,
                                       0 AS depth
                                FROM commit_meta cm
                                JOIN author a ON cm.author_id = a.id
                                WHERE cm.id = :head_commit_id
                                UNION ALL
                                SELECT cm.id, cm.parent_commit_id, cm.hash, cm.timestamp, cm.message,
                                       a.name AS author_name, a.email AS author_email,
                                       cc.depth + 1
                                FROM commit_meta cm
                                JOIN author a ON cm.author_id = a.id
                                JOIN commit_chain cc ON cm.id = cc.parent_commit_id
                            )
                            SELECT
                                cl.id AS change_log_id,
                                cc.hash AS commit_hash,
                                cc.timestamp AS commit_timestamp,
                                cc.message AS commit_message,
                                cc.author_name,
                                cc.author_email,
                                e.id AS entity_id,
                                e.name AS entity_name,
                                e.language AS entity_language,
                                e.kind AS entity_kind,
                                cl.file_path,
                                cl.operation,
                                cl.nature_flag,
                                cl.data_quality,
                                cl.analysis_type,
                                pe.id AS parent_entity_id,
                                pe.name AS parent_entity_name,
                                pe.language AS parent_entity_language,
                                pe.kind AS parent_entity_kind
                            FROM change_log cl
                            JOIN entity e ON cl.entity_id = e.id
                            JOIN commit_chain cc ON cl.commit_id = cc.id
                            LEFT JOIN entity pe ON cl.parent_entity_id = pe.id
                            WHERE e.name = :entity_name
                            ORDER BY cc.depth DESC
                            """)
                        .bind("head_commit_id", headCommitId)
                        .bind("entity_name", entityName)
                        .map((rs, ctx) -> buildChangeLog(rs))
                        .forEach(changes::add);
            });
        } catch (Exception e) {
            log.error("Failed to query entity change history for entity={} ref={}", entityName, refName, e);
        }
        return EntityChangeHistory.builder().changes(changes).build();
    }

    @Override
    public List<Entity> findAllEntities() {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT id, name, language, kind FROM entity ORDER BY name")
                    .map((rs, ctx) -> Entity.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .language(EntityLanguage.fromCode(rs.getInt("language")))
                        .kind(EntityKind.fromCode(rs.getInt("kind")))
                        .build())
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to find all entities", e);
            return List.of();
        }
    }

    @Override
    public AuthorChangeStatistics queryAuthorChangeStatistics(Author author, String refName) {
        try {
            Map<ChangeOperation, Float> operationFloatMap = new EnumMap<>(ChangeOperation.class);
            Map<ChangeNatureFlag, Float> natureFlagFloatMap = new EnumMap<>(ChangeNatureFlag.class);

            if (refName != null) {
                Long headCommitId = resolveRefCommitId(refName);
                if (headCommitId == null) {
                    log.warn("Ref not found: {}", refName);
                    return null;
                }
                fillAuthorStatisticsWithRef(author, headCommitId, operationFloatMap, natureFlagFloatMap);
            } else {
                fillAuthorStatisticsNoRef(author, operationFloatMap, natureFlagFloatMap);
            }

            return AuthorChangeStatistics.builder()
                    .author(author)
                    .operationFloatMap(operationFloatMap)
                    .natureFlagFloatMap(natureFlagFloatMap)
                    .build();
        } catch (Exception e) {
            log.error("Failed to query author change statistics for author={} ref={}", author, refName, e);
            return null;
        }
    }

    private void fillAuthorStatisticsNoRef(Author author,
                                           Map<ChangeOperation, Float> operationFloatMap,
                                           Map<ChangeNatureFlag, Float> natureFlagFloatMap) {
        dbManager.getJdbi().useHandle(handle ->
            handle.createQuery("""
                    WITH commit_counts AS (
                        SELECT cl.commit_id, COUNT(*) AS cnt
                        FROM change_log cl
                        JOIN commit_meta cm ON cl.commit_id = cm.id
                        JOIN author a ON cm.author_id = a.id
                        WHERE a.name = :author_name AND a.email = :author_email
                        GROUP BY cl.commit_id
                    ),
                    total_commits AS (
                        SELECT COUNT(*) AS total FROM commit_counts
                    ),
                    weighted AS (
                        SELECT
                            cl.operation,
                            COALESCE(cl.nature_flag, 0) AS nature_flag,
                            1.0 / cc.cnt AS w
                        FROM change_log cl
                        JOIN commit_counts cc ON cc.commit_id = cl.commit_id
                    ),
                    op_stats AS (
                        SELECT
                            operation AS value,
                            SUM(w) / (SELECT total FROM total_commits) AS operation_ratio
                        FROM weighted
                        GROUP BY operation
                    ),
                    nf_stats AS (
                        SELECT
                            nature_flag AS value,
                            SUM(w) / (SELECT total FROM total_commits) AS nature_flag_ratio
                        FROM weighted
                        GROUP BY nature_flag
                    ),
                    all_values AS (
                        SELECT value FROM op_stats
                        UNION
                        SELECT value FROM nf_stats
                    )
                    SELECT
                        av.value,
                        ROUND(COALESCE(op.operation_ratio, 0), 6) AS operation_ratio,
                        ROUND(COALESCE(nf.nature_flag_ratio, 0), 6) AS nature_flag_ratio
                    FROM all_values av
                    LEFT JOIN op_stats op ON op.value = av.value
                    LEFT JOIN nf_stats nf ON nf.value = av.value
                    ORDER BY av.value
                    """)
                .bind("author_name", author.getName())
                .bind("author_email", author.getEmail())
                .map((rs, ctx) -> {
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
                    return null;
                }).list()
        );
    }

    private void fillAuthorStatisticsWithRef(Author author, Long headCommitId,
                                             Map<ChangeOperation, Float> operationFloatMap,
                                             Map<ChangeNatureFlag, Float> natureFlagFloatMap) {
        dbManager.getJdbi().useHandle(handle ->
            handle.createQuery("""
                    WITH RECURSIVE commit_chain AS (
                        SELECT cm.id
                        FROM commit_meta cm
                        WHERE cm.id = :head_commit_id
                        UNION ALL
                        SELECT cm.id
                        FROM commit_meta cm
                        JOIN commit_chain cc ON cm.id = cc.parent_commit_id
                    ),
                    commit_counts AS (
                        SELECT cl.commit_id, COUNT(*) AS cnt
                        FROM change_log cl
                        JOIN commit_meta cm ON cl.commit_id = cm.id
                        JOIN author a ON cm.author_id = a.id
                        JOIN commit_chain ch ON cm.id = ch.id
                        WHERE a.name = :author_name AND a.email = :author_email
                        GROUP BY cl.commit_id
                    ),
                    total_commits AS (
                        SELECT COUNT(*) AS total FROM commit_counts
                    ),
                    weighted AS (
                        SELECT
                            cl.operation,
                            COALESCE(cl.nature_flag, 0) AS nature_flag,
                            1.0 / cc.cnt AS w
                        FROM change_log cl
                        JOIN commit_counts cc ON cc.commit_id = cl.commit_id
                    ),
                    op_stats AS (
                        SELECT
                            operation AS value,
                            SUM(w) / (SELECT total FROM total_commits) AS operation_ratio
                        FROM weighted
                        GROUP BY operation
                    ),
                    nf_stats AS (
                        SELECT
                            nature_flag AS value,
                            SUM(w) / (SELECT total FROM total_commits) AS nature_flag_ratio
                        FROM weighted
                        GROUP BY nature_flag
                    ),
                    all_values AS (
                        SELECT value FROM op_stats
                        UNION
                        SELECT value FROM nf_stats
                    )
                    SELECT
                        av.value,
                        ROUND(COALESCE(op.operation_ratio, 0), 6) AS operation_ratio,
                        ROUND(COALESCE(nf.nature_flag_ratio, 0), 6) AS nature_flag_ratio
                    FROM all_values av
                    LEFT JOIN op_stats op ON op.value = av.value
                    LEFT JOIN nf_stats nf ON nf.value = av.value
                    ORDER BY av.value
                    """)
                .bind("head_commit_id", headCommitId)
                .bind("author_name", author.getName())
                .bind("author_email", author.getEmail())
                .map((rs, ctx) -> {
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
                    return null;
                }).list()
        );
    }

    private void debugChainCount(String entityName, Long headCommitId) {
        try {
            dbManager.getJdbi().useHandle(handle ->
                handle.createQuery("""
                        WITH RECURSIVE commit_chain AS (
                            SELECT cm.id, cm.parent_commit_id, cm.hash, 0 AS depth
                            FROM commit_meta cm
                            WHERE cm.id = :head_commit_id
                            UNION ALL
                            SELECT cm.id, cm.parent_commit_id, cm.hash, cc.depth + 1
                            FROM commit_meta cm
                            JOIN commit_chain cc ON cm.id = cc.parent_commit_id
                        )
                        SELECT COUNT(*) FROM commit_chain
                        """)
                    .bind("head_commit_id", headCommitId)
                    .mapTo(Integer.class)
                    .findOne()
                    .ifPresent(count ->
                        log.debug("commit_chain: {} commits reachable from headCommitId={}", count, headCommitId))
            );
        } catch (Exception e) {
            log.debug("Chain count query failed", e);
        }
    }

    private void debugCommitParentStats() {
        try {
            dbManager.getJdbi().useHandle(handle ->
                handle.createQuery(
                    "SELECT COUNT(*) AS total, SUM(CASE WHEN parent_commit_id IS NOT NULL THEN 1 ELSE 0 END) AS with_parent FROM commit_meta")
                    .map((rs, ctx) -> {
                        log.debug("commit_meta: {} total, {} have parent_commit_id", rs.getInt("total"), rs.getInt("with_parent"));
                        return null;
                    })
                    .list()
            );
        } catch (Exception e) {
            log.debug("Parent stats query failed", e);
        }
    }

    private void debugEntityInfo(String entityName) {
        try {
            dbManager.getJdbi().useHandle(handle -> {
                handle.createQuery("SELECT COUNT(*) FROM entity WHERE name = :name")
                    .bind("name", entityName)
                    .mapTo(Integer.class)
                    .findOne()
                    .ifPresent(count -> log.debug("entity table: {} rows matching '{}'", count, entityName));

                handle.createQuery("SELECT COUNT(*) FROM change_log cl JOIN entity e ON cl.entity_id = e.id WHERE e.name = :name")
                    .bind("name", entityName)
                    .mapTo(Integer.class)
                    .findOne()
                    .ifPresent(count -> log.debug("change_log table: {} rows matching '{}'", count, entityName));
            });
        } catch (Exception e) {
            log.debug("Debug query failed", e);
        }
    }

    private Long resolveRefCommitId(String refName) {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT commit_id FROM ref WHERE name = :name")
                    .bind("name", refName)
                    .mapTo(Long.class)
                    .findOne()
                    .orElse(null)
            );
        } catch (Exception e) {
            log.error("Failed to resolve ref commit id for ref: {}", refName, e);
            return null;
        }
    }

    private ChangeLog buildChangeLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        byte[] hashBytes = rs.getBytes("commit_hash");
        String hash = HexFormat.of().formatHex(hashBytes);

        Author author = Author.builder()
                .name(rs.getString("author_name"))
                .email(rs.getString("author_email"))
                .build();

        CommitMeta commit = CommitMeta.builder()
                .hash(hash)
                .author(author)
                .timestamp(rs.getInt("commit_timestamp"))
                .message(rs.getString("commit_message"))
                .build();

        Entity entity = Entity.builder()
                .id(rs.getLong("entity_id"))
                .name(rs.getString("entity_name"))
                .language(EntityLanguage.fromCode(rs.getInt("entity_language")))
                .kind(EntityKind.fromCode(rs.getInt("entity_kind")))
                .build();

        long parentEntityId = rs.getLong("parent_entity_id");
        Entity parentEntity = null;
        if (!rs.wasNull()) {
            parentEntity = Entity.builder()
                    .id(parentEntityId)
                    .name(rs.getString("parent_entity_name"))
                    .language(EntityLanguage.fromCode(rs.getInt("parent_entity_language")))
                    .kind(EntityKind.fromCode(rs.getInt("parent_entity_kind")))
                    .build();
        }

        return ChangeLog.builder()
                .id(rs.getLong("change_log_id"))
                .commit(commit)
                .entity(entity)
                .filePath(rs.getString("file_path"))
                .operation(ChangeOperation.fromCode(rs.getInt("operation")))
                .natureFlagCode(rs.getInt("nature_flag"))
                .parentEntity(parentEntity)
                .dataQuality(DataQuality.fromCode(rs.getInt("data_quality")))
                .analysisType(AnalysisType.fromCode(rs.getInt("analysis_type")))
                .build();
    }

    private @NonNull Long resolveCommitId(Handle handle, String hash) {
        byte[] hashBytes;
        try {
            hashBytes = HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid commit hash: " + hash, e);
        }
        return handle.createQuery("SELECT id FROM commit_meta WHERE hash = :hash")
                .bind("hash", hashBytes)
                .mapTo(Long.class)
                .findOne()
                .orElseThrow(() -> new RuntimeException("Commit not found: " + hash));
    }

    private @NonNull Long upsertEntity(Handle handle, Entity entity) {
        return handle.createUpdate("""
                            INSERT INTO entity (name, language, kind) VALUES (:name, :language, :kind)
                            ON CONFLICT(name, language, kind) DO UPDATE SET name = excluded.name
                            RETURNING id
                        """)
                .bind("name", entity.getName())
                .bind("language", entity.getLanguage().code)
                .bind("kind", entity.getKind().code)
                .executeAndReturnGeneratedKeys()
                .mapTo(Long.class)
                .one();
    }

    private void insertChangeLog(Handle handle, Long commitId, Long entityId,
                                 ChangeLog changeLog, Long parentEntityId) {
        handle.createUpdate("""
                            INSERT OR IGNORE INTO change_log (commit_id, entity_id, file_path, operation, nature_flag, parent_entity_id, data_quality, analysis_type)
                            VALUES (:commit_id, :entity_id, :file_path, :operation, :nature_flag, :parent_entity_id, :data_quality, :analysis_type)
                        """)
                .bind("commit_id", commitId)
                .bind("entity_id", entityId)
                .bind("file_path", changeLog.getFilePath())
                .bind("operation", changeLog.getOperation().code)
                .bind("nature_flag", changeLog.getNatureFlagCode())
                .bind("parent_entity_id", parentEntityId)
                .bind("data_quality", changeLog.getDataQuality().code)
                .bind("analysis_type", changeLog.getAnalysisType().code)
                .execute();
    }
}
