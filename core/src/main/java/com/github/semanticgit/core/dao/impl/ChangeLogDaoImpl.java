package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.*;
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
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                Long commitId = resolveCommitId(conn, changeLog.getCommit().getHash());
                Long entityId = upsertEntity(conn, changeLog.getEntity());
                Long parentEntityId = changeLog.getParentEntity() != null
                        ? upsertEntity(conn, changeLog.getParentEntity())
                        : null;
                insertChangeLog(conn, commitId, entityId, changeLog, parentEntityId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to save change log for commit {}: {}", changeLog.getCommit().getHash(), e.getMessage());
        }
    }

    @Override
    public void saveAll(@NonNull List<ChangeLog> changeLogs) {
        if (changeLogs.isEmpty()) {
            return;
        }
        try {
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                for (ChangeLog changeLog : changeLogs) {
                    Long commitId = resolveCommitId(conn, changeLog.getCommit().getHash());
                    Long entityId = upsertEntity(conn, changeLog.getEntity());
                    Long parentEntityId = changeLog.getParentEntity() != null
                            ? upsertEntity(conn, changeLog.getParentEntity())
                            : null;
                    insertChangeLog(conn, commitId, entityId, changeLog, parentEntityId);
                }
                conn.commit();
                log.info("Saved {} change logs to database", changeLogs.size());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to save change logs batch", e);
        }
    }

    @Override
    public ResultSet querySimpleEntityChangeStatistics() throws SQLException {
        Statement stmt = dbManager.getConnection().createStatement();
        return stmt.executeQuery("""
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
                     ORDER BY av.value;
                     """);
    }

    @Override
    public ResultSet queryCommitEntityChangeStatistics(String hash) throws SQLException {
        byte[] hashBytes = HexFormat.of().parseHex(hash);
        String sql = """
                     WITH total AS (
                         SELECT COUNT(*) AS cnt
                         FROM change_log cl
                         JOIN commit_meta cm ON cl.commit_id = cm.id
                         WHERE cm.hash = ?
                     ),
                     op_stats AS (
                         SELECT
                             cl.operation AS value,
                             CAST(COUNT(*) AS REAL) / (SELECT cnt FROM total) AS operation_ratio
                         FROM change_log cl
                         JOIN commit_meta cm ON cl.commit_id = cm.id
                         WHERE cm.hash = ?
                         GROUP BY cl.operation
                     ),
                     nf_stats AS (
                         SELECT
                             COALESCE(cl.nature_flag, 0) AS value,
                             CAST(COUNT(*) AS REAL) / (SELECT cnt FROM total) AS nature_flag_ratio
                         FROM change_log cl
                         JOIN commit_meta cm ON cl.commit_id = cm.id
                         WHERE cm.hash = ?
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
                     ORDER BY av.value;
                     """;
        PreparedStatement ps = dbManager.getConnection().prepareStatement(sql);
        ps.setBytes(1, hashBytes);
        ps.setBytes(2, hashBytes);
        ps.setBytes(3, hashBytes);
        return ps.executeQuery();
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

            String sql = """
                        WITH RECURSIVE commit_chain AS (
                            SELECT cm.id, cm.parent_commit_id, cm.hash, cm.timestamp, cm.message,
                                   a.name AS author_name, a.email AS author_email,
                                   0 AS depth
                            FROM commit_meta cm
                            JOIN author a ON cm.author_id = a.id
                            WHERE cm.id = ?
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
                        WHERE e.name = ?
                        ORDER BY cc.depth DESC
                        """;
            try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
                ps.setLong(1, headCommitId);
                ps.setString(2, entityName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        changes.add(buildChangeLog(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query entity change history for entity={} ref={}", entityName, refName, e);
        }
        return EntityChangeHistory.builder().changes(changes).build();
    }

    @Override
    public List<Entity> findAllEntities() {
        List<Entity> entities = new ArrayList<>();
        String sql = "SELECT id, name, language, kind FROM entity ORDER BY name";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                entities.add(Entity.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .language(EntityLanguage.fromCode(rs.getInt("language")))
                        .kind(EntityKind.fromCode(rs.getInt("kind")))
                        .build());
            }
        } catch (SQLException e) {
            log.error("Failed to find all entities", e);
        }
        return entities;
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
        } catch (SQLException e) {
            log.error("Failed to query author change statistics for author={} ref={}", author, refName, e);
            return null;
        }
    }

    private void fillAuthorStatisticsNoRef(Author author,
                                           Map<ChangeOperation, Float> operationFloatMap,
                                           Map<ChangeNatureFlag, Float> natureFlagFloatMap) throws SQLException {
        String sql = """
            WITH commit_counts AS (
                SELECT cl.commit_id, COUNT(*) AS cnt
                FROM change_log cl
                JOIN commit_meta cm ON cl.commit_id = cm.id
                JOIN author a ON cm.author_id = a.id
                WHERE a.name = ? AND a.email = ?
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
            """;
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, author.getName());
            ps.setString(2, author.getEmail());
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        }
    }

    private void fillAuthorStatisticsWithRef(Author author, Long headCommitId,
                                             Map<ChangeOperation, Float> operationFloatMap,
                                             Map<ChangeNatureFlag, Float> natureFlagFloatMap) throws SQLException {
        String sql = """
            WITH RECURSIVE commit_chain AS (
                SELECT cm.id
                FROM commit_meta cm
                WHERE cm.id = ?
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
                WHERE a.name = ? AND a.email = ?
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
            """;
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, headCommitId);
            ps.setString(2, author.getName());
            ps.setString(3, author.getEmail());
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        }
    }

    private void debugChainCount(String entityName, Long headCommitId) {
        String sql = """
                WITH RECURSIVE commit_chain AS (
                    SELECT cm.id, cm.parent_commit_id, cm.hash, 0 AS depth
                    FROM commit_meta cm
                    WHERE cm.id = ?
                    UNION ALL
                    SELECT cm.id, cm.parent_commit_id, cm.hash, cc.depth + 1
                    FROM commit_meta cm
                    JOIN commit_chain cc ON cm.id = cc.parent_commit_id
                )
                SELECT COUNT(*) FROM commit_chain
                """;
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, headCommitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    log.debug("commit_chain: {} commits reachable from headCommitId={}", rs.getInt(1), headCommitId);
                }
            }
        } catch (SQLException e) {
            log.debug("Chain count query failed", e);
        }
    }

    private void debugCommitParentStats() {
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) AS total, SUM(CASE WHEN parent_commit_id IS NOT NULL THEN 1 ELSE 0 END) AS with_parent FROM commit_meta");
            if (rs.next()) {
                log.debug("commit_meta: {} total, {} have parent_commit_id", rs.getInt("total"), rs.getInt("with_parent"));
            }
        } catch (SQLException e) {
            log.debug("Parent stats query failed", e);
        }
    }

    private void debugEntityInfo(String entityName) {
        try {
            String countSql = "SELECT COUNT(*) FROM entity WHERE name = ?";
            try (PreparedStatement ps = dbManager.getConnection().prepareStatement(countSql)) {
                ps.setString(1, entityName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        log.debug("entity table: {} rows matching '{}'", rs.getInt(1), entityName);
                    }
                }
            }
            String clSql = "SELECT COUNT(*) FROM change_log cl JOIN entity e ON cl.entity_id = e.id WHERE e.name = ?";
            try (PreparedStatement ps = dbManager.getConnection().prepareStatement(clSql)) {
                ps.setString(1, entityName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        log.debug("change_log table: {} rows matching '{}'", rs.getInt(1), entityName);
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("Debug query failed", e);
        }
    }

    private Long resolveRefCommitId(String refName) throws SQLException {
        String sql = "SELECT commit_id FROM ref WHERE name = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, refName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("commit_id");
                }
            }
        }
        return null;
    }

    private ChangeLog buildChangeLog(ResultSet rs) throws SQLException {
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

    private @NonNull Long resolveCommitId(Connection conn, String hash) throws SQLException {
        byte[] hashBytes;
        try {
            hashBytes = HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid commit hash: " + hash, e);
        }
        String sql = "SELECT id FROM commit_meta WHERE hash = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBytes(1, hashBytes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Commit not found: " + hash);
    }

    private @NonNull Long upsertEntity(Connection conn, Entity entity) throws SQLException {
        String sql = """
                    INSERT INTO entity (name, language, kind) VALUES (?, ?, ?)
                    ON CONFLICT(name, language, kind) DO UPDATE SET name = excluded.name
                    RETURNING id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getName());
            ps.setInt(2, entity.getLanguage().code);
            ps.setInt(3, entity.getKind().code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to upsert entity: " + entity.getName());
    }

    private void insertChangeLog(Connection conn, Long commitId, Long entityId,
                                 ChangeLog changeLog, Long parentEntityId) throws SQLException {
        String sql = """
                    INSERT OR IGNORE INTO change_log (commit_id, entity_id, file_path, operation, nature_flag, parent_entity_id, data_quality, analysis_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, commitId);
            ps.setLong(2, entityId);
            ps.setString(3, changeLog.getFilePath());
            ps.setInt(4, changeLog.getOperation().code);
            ps.setInt(5, changeLog.getNatureFlagCode());
            if (parentEntityId != null) {
                ps.setLong(6, parentEntityId);
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            ps.setInt(7, changeLog.getDataQuality().code);
            ps.setInt(8, changeLog.getAnalysisType().code);
            ps.executeUpdate();
        }
    }
}
