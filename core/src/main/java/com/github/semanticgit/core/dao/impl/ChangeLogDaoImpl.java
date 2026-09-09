package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.db.DatabaseManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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

    private @NonNull Long resolveCommitId(Connection conn, String hash) throws SQLException {
        String sql = "SELECT id FROM commit_meta WHERE hash = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
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
            ps.setInt(5, changeLog.getNatureFlag() != null ? changeLog.getNatureFlag().code : 0);
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
