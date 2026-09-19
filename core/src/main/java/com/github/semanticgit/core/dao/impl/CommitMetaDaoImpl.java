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
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
                upsertCommit(conn, commit, authorId, null);
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
                Map<String, Long> hashToId = new HashMap<>();
                for (CommitMeta commit : commits) {
                    Long authorId = upsertAuthor(conn, commit.getAuthor());
                    CommitMeta parent = commit.getParentCommitMeta();
                    Long parentCommitId = parent != null ? hashToId.get(parent.getHash()) : null;
                    Long commitId = upsertCommit(conn, commit, authorId, parentCommitId);
                    hashToId.put(commit.getHash(), commitId);
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

    @Override
    public CommitMeta findByHash(String hash) throws SQLException {
        byte[] hashBytes = HexFormat.of().parseHex(hash);
        String sql = """
            SELECT cm.id, cm.timestamp, cm.message, cm.parent_commit_id,
                   a.name, a.email,
                   p.hash AS parent_hash, p.timestamp AS parent_timestamp, p.message AS parent_message,
                   pa.name AS parent_author_name, pa.email AS parent_author_email
            FROM commit_meta cm
            JOIN author a ON cm.author_id = a.id
            LEFT JOIN commit_meta p ON cm.parent_commit_id = p.id
            LEFT JOIN author pa ON p.author_id = pa.id
            WHERE cm.hash = ?
            """;
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setBytes(1, hashBytes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Author author = Author.builder()
                            .name(rs.getString("name"))
                            .email(rs.getString("email"))
                            .build();

                    long parentCommitId = rs.getLong("parent_commit_id");
                    CommitMeta parentCommitMeta = null;
                    if (!rs.wasNull()) {
                        byte[] parentHashBytes = rs.getBytes("parent_hash");
                        Author parentAuthor = Author.builder()
                                .name(rs.getString("parent_author_name"))
                                .email(rs.getString("parent_author_email"))
                                .build();
                        parentCommitMeta = CommitMeta.builder()
                                .id(parentCommitId)
                                .hash(HexFormat.of().formatHex(parentHashBytes))
                                .author(parentAuthor)
                                .timestamp(rs.getInt("parent_timestamp"))
                                .message(rs.getString("parent_message"))
                                .build();
                    }

                    return CommitMeta.builder()
                            .id(rs.getLong("id"))
                            .hash(hash)
                            .author(author)
                            .timestamp(rs.getInt("timestamp"))
                            .message(rs.getString("message"))
                            .parentCommitMeta(parentCommitMeta)
                            .build();
                }
            }
        }
        return null;
    }

    @Override
    public List<CommitMeta> findAll() {
        List<CommitMeta> commits = new ArrayList<>();
        String sql = """
            SELECT cm.id, cm.hash, cm.timestamp, cm.message,
                   a.name AS author_name, a.email AS author_email,
                   p.hash AS parent_hash, p.timestamp AS parent_timestamp, p.message AS parent_message,
                   pa.name AS parent_author_name, pa.email AS parent_author_email
            FROM commit_meta cm
            JOIN author a ON cm.author_id = a.id
            LEFT JOIN commit_meta p ON cm.parent_commit_id = p.id
            LEFT JOIN author pa ON p.author_id = pa.id
            ORDER BY cm.timestamp DESC
            """;
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                commits.add(buildCommitMetaFromResultSet(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find all commits", e);
        }
        return commits;
    }

    @Override
    public List<Author> findAllAuthors() {
        List<Author> authors = new ArrayList<>();
        String sql = "SELECT id, name, email FROM author ORDER BY name";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                authors.add(Author.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .email(rs.getString("email"))
                        .build());
            }
        } catch (SQLException e) {
            log.error("Failed to find all authors", e);
        }
        return authors;
    }

    private CommitMeta buildCommitMetaFromResultSet(ResultSet rs) throws SQLException {
        byte[] hashBytes = rs.getBytes("hash");
        String hash = HexFormat.of().formatHex(hashBytes);

        Author author = Author.builder()
                .name(rs.getString("author_name"))
                .email(rs.getString("author_email"))
                .build();

        byte[] parentHashBytes = rs.getBytes("parent_hash");
        CommitMeta parentCommitMeta = null;
        if (!rs.wasNull()) {
            Author parentAuthor = Author.builder()
                    .name(rs.getString("parent_author_name"))
                    .email(rs.getString("parent_author_email"))
                    .build();
            parentCommitMeta = CommitMeta.builder()
                    .hash(HexFormat.of().formatHex(parentHashBytes))
                    .author(parentAuthor)
                    .timestamp(rs.getInt("parent_timestamp"))
                    .message(rs.getString("parent_message"))
                    .build();
        }

        return CommitMeta.builder()
                .id(rs.getLong("id"))
                .hash(hash)
                .author(author)
                .timestamp(rs.getInt("timestamp"))
                .message(rs.getString("message"))
                .parentCommitMeta(parentCommitMeta)
                .build();
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

    private @NonNull Long upsertCommit(@NonNull Connection conn, @NonNull CommitMeta commit,
                                        Long authorId, Long parentCommitId) throws SQLException {
        String insertSql = """
            INSERT OR IGNORE INTO commit_meta (hash, author_id, timestamp, message, parent_commit_id)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setBytes(1, HexFormat.of().parseHex(commit.getHash()));
            ps.setLong(2, authorId);
            ps.setInt(3, commit.getTimestamp());
            ps.setString(4, commit.getMessage());
            if (parentCommitId != null) {
                ps.setLong(5, parentCommitId);
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.executeUpdate();
        }

        String selectSql = "SELECT id FROM commit_meta WHERE hash = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setBytes(1, HexFormat.of().parseHex(commit.getHash()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to upsert commit: " + commit.getHash());
    }
}
