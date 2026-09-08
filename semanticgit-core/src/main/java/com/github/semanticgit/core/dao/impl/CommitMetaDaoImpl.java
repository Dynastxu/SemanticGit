package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.db.DatabaseManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Slf4j
public class CommitMetaDaoImpl implements CommitMetaDao {
    private final DatabaseManager dbManager;

    public CommitMetaDaoImpl(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void save(@NonNull CommitMeta commit) {
        try {
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                Long authorId = upsertAuthor(conn, commit.getAuthor());
                insertCommit(conn, commit, authorId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to save commit: {}", commit.getHash(), e);
        }
    }

    @Override
    public void saveAll(@NonNull List<CommitMeta> commits) {
        try {
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                for (CommitMeta commit : commits) {
                    Long authorId = upsertAuthor(conn, commit.getAuthor());
                    insertCommit(conn, commit, authorId);
                }
                conn.commit();
                log.info("Saved {} commits to database", commits.size());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to save commits batch", e);
        }
    }

    private @NonNull Long upsertAuthor(Connection conn, @NonNull Author author) throws SQLException {
        String sql = """
            INSERT INTO author (name, email) VALUES (?, ?)
            ON CONFLICT(name, email) DO UPDATE SET name = excluded.name
            RETURNING id
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, author.getName());
            ps.setString(2, author.getEmail());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to upsert author");
    }

    private void insertCommit(@NonNull Connection conn, @NonNull CommitMeta commit, Long authorId) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO commit_meta (hash, author_id, timestamp, message)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, commit.getHash());
            ps.setLong(2, authorId);
            ps.setInt(3, commit.getTimestamp());
            ps.setString(4, commit.getMessage());
            ps.executeUpdate();
        }
    }
}
