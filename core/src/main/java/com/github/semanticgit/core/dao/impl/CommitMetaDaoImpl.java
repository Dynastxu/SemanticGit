package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.db.DatabaseManager;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
            dbManager.getJdbi().useTransaction(handle -> {
                Long authorId = upsertAuthor(handle, commit.getAuthor());
                upsertCommit(handle, commit, authorId, null);
            });
        } catch (Exception e) {
            log.error("Failed to save commit: {}", commit.getHash(), e);
        }
    }

    @Override
    public void saveAll(@NonNull List<CommitMeta> commits) {
        try {
            dbManager.getJdbi().useTransaction(handle -> {
                Map<String, Long> hashToId = new HashMap<>();
                for (CommitMeta commit : commits) {
                    Long authorId = upsertAuthor(handle, commit.getAuthor());
                    CommitMeta parent = commit.getParentCommitMeta();
                    Long parentCommitId = resolveParentCommitId(handle, hashToId, parent);
                    Long commitId = upsertCommit(handle, commit, authorId, parentCommitId);
                    hashToId.put(commit.getHash(), commitId);
                }
            });
            log.info("Saved {} commits to database", commits.size());
        } catch (Exception e) {
            log.error("Failed to save commits batch", e);
        }
    }

    @Override
    public CommitMeta findByHash(String hash) {
        byte[] hashBytes = HexFormat.of().parseHex(hash);
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("""
                            SELECT cm.id, cm.timestamp, cm.message, cm.parent_commit_id,
                                   a.name, a.email,
                                   p.hash AS parent_hash, p.timestamp AS parent_timestamp, p.message AS parent_message,
                                   pa.name AS parent_author_name, pa.email AS parent_author_email
                            FROM commit_meta cm
                            JOIN author a ON cm.author_id = a.id
                            LEFT JOIN commit_meta p ON cm.parent_commit_id = p.id
                            LEFT JOIN author pa ON p.author_id = pa.id
                            WHERE cm.hash = :hash
                            """)
                    .bind("hash", hashBytes)
                    .map((rs, ctx) -> {
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
                    })
                    .findOne()
                    .orElse(null)
            );
        } catch (Exception e) {
            log.error("Failed to find commit by hash: {}", hash, e);
            return null;
        }
    }

    @Override
    public List<CommitMeta> findAll() {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("""
                            SELECT cm.id, cm.hash, cm.timestamp, cm.message,
                                   a.name AS author_name, a.email AS author_email,
                                   p.hash AS parent_hash, p.timestamp AS parent_timestamp, p.message AS parent_message,
                                   pa.name AS parent_author_name, pa.email AS parent_author_email
                            FROM commit_meta cm
                            JOIN author a ON cm.author_id = a.id
                            LEFT JOIN commit_meta p ON cm.parent_commit_id = p.id
                            LEFT JOIN author pa ON p.author_id = pa.id
                            ORDER BY cm.timestamp DESC
                            """)
                    .map((rs, ctx) -> buildCommitMetaFromResultSet(rs))
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to find all commits", e);
            return List.of();
        }
    }

    @Override
    public CommitMeta findLatest() {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("""
                            SELECT cm.id, cm.hash, cm.timestamp, cm.message,
                                   a.name AS author_name, a.email AS author_email
                            FROM commit_meta cm
                            JOIN author a ON cm.author_id = a.id
                            ORDER BY cm.timestamp DESC, cm.id DESC
                            LIMIT 1
                            """)
                    .map((rs, ctx) -> {
                        byte[] hashBytes = rs.getBytes("hash");
                        String hash = HexFormat.of().formatHex(hashBytes);
                        Author author = Author.builder()
                                .name(rs.getString("author_name"))
                                .email(rs.getString("author_email"))
                                .build();
                        return CommitMeta.builder()
                                .id(rs.getLong("id"))
                                .hash(hash)
                                .author(author)
                                .timestamp(rs.getInt("timestamp"))
                                .message(rs.getString("message"))
                                .build();
                    })
                    .findOne()
                    .orElse(null)
            );
        } catch (Exception e) {
            log.error("Failed to find latest commit", e);
            return null;
        }
    }

    @Override
    public List<Author> findAllAuthors() {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT id, name, email FROM author ORDER BY name")
                    .map((rs, ctx) -> Author.builder()
                        .id(rs.getLong("id"))
                        .name(rs.getString("name"))
                        .email(rs.getString("email"))
                        .build())
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to find all authors", e);
            return List.of();
        }
    }

    @Override
    public int count() {
        try {
            return dbManager.getJdbi().withHandle(handle -> {
                Integer count = handle.createQuery("SELECT COUNT(*) FROM commit_meta")
                        .mapTo(Integer.class)
                        .one();
                log.info("countCommits result: {}", count);
                return count;
            });
        } catch (Exception e) {
            log.error("Failed to count commits", e);
            return 0;
        }
    }

    private CommitMeta buildCommitMetaFromResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
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

    @Nullable
    private Long resolveParentCommitId(Handle handle, Map<String, Long> hashToId,
                                       CommitMeta parent) {
        if (parent == null) {
            return null;
        }
        Long id = hashToId.get(parent.getHash());
        if (id != null) {
            return id;
        }
        return handle.createQuery("SELECT id FROM commit_meta WHERE hash = :hash")
                .bind("hash", HexFormat.of().parseHex(parent.getHash()))
                .mapTo(Long.class)
                .findOne()
                .orElse(null);
    }

    private @NonNull Long upsertAuthor(Handle handle, @NonNull Author author) {
        Long existingId = handle.createQuery(
                        "SELECT id FROM author WHERE name = :name AND email = :email")
                .bind("name", author.getName())
                .bind("email", author.getEmail())
                .mapTo(Long.class)
                .findOne()
                .orElse(null);

        if (existingId != null) {
            return existingId;
        }

        return handle.createUpdate(
                        "INSERT INTO author (name, email) VALUES (:name, :email)")
                .bind("name", author.getName())
                .bind("email", author.getEmail())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
    }

    private @NonNull Long upsertCommit(Handle handle, @NonNull CommitMeta commit,
                                        Long authorId, Long parentCommitId) {
        handle.createUpdate("""
                    INSERT OR IGNORE INTO commit_meta (hash, author_id, timestamp, message, parent_commit_id)
                    VALUES (:hash, :author_id, :timestamp, :message, :parent_commit_id)
                """)
                .bind("hash", HexFormat.of().parseHex(commit.getHash()))
                .bind("author_id", authorId)
                .bind("timestamp", commit.getTimestamp())
                .bind("message", commit.getMessage())
                .bind("parent_commit_id", parentCommitId)
                .execute();

        return handle.createQuery("SELECT id FROM commit_meta WHERE hash = :hash")
                .bind("hash", HexFormat.of().parseHex(commit.getHash()))
                .mapTo(Long.class)
                .one();
    }
}
