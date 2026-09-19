package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.ReferenceType;
import com.github.semanticgit.common.entity.References;
import com.github.semanticgit.core.dao.RefDao;
import com.github.semanticgit.core.db.DatabaseManager;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

@Slf4j
public class RefDaoImpl implements RefDao {
    private final DatabaseManager dbManager;

    public RefDaoImpl(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void save(@NonNull References ref) {
        try {
            dbManager.getJdbi().useHandle(handle ->
                handle.createUpdate("""
                            INSERT OR REPLACE INTO ref (name, kind, commit_id) VALUES (:name, :kind, :commit_id)
                            """)
                    .bind("name", ref.getName())
                    .bind("kind", ref.getType().code)
                    .bind("commit_id", ref.getCommitId())
                    .execute()
            );
        } catch (Exception e) {
            log.error("Failed to save ref: {}", ref.getName(), e);
        }
    }

    @Override
    public void saveAll(@NonNull List<References> refs) {
        if (refs.isEmpty()) {
            return;
        }
        try {
            dbManager.getJdbi().useTransaction(handle -> {
                PreparedBatch batch = handle.prepareBatch("""
                            INSERT OR REPLACE INTO ref (name, kind, commit_id) VALUES (:name, :kind, :commit_id)
                            """);
                for (References ref : refs) {
                    batch.bind("name", ref.getName())
                         .bind("kind", ref.getType().code)
                         .bind("commit_id", ref.getCommitId())
                         .add();
                }
                batch.execute();
            });
            log.info("Saved {} refs to database", refs.size());
        } catch (Exception e) {
            log.error("Failed to save refs batch", e);
        }
    }

    @Override
    public Optional<References> findByName(String name) {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT name, kind, commit_id FROM ref WHERE name = :name")
                    .bind("name", name)
                    .map((rs, ctx) -> References.builder()
                        .name(rs.getString("name"))
                        .type(ReferenceType.fromCode(rs.getInt("kind")))
                        .commitId(rs.getLong("commit_id"))
                        .build())
                    .findOne()
            );
        } catch (Exception e) {
            log.error("Failed to find ref by name: {}", name, e);
            return Optional.empty();
        }
    }

    @Override
    public List<References> findAll() {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT name, kind, commit_id FROM ref")
                    .map((rs, ctx) -> References.builder()
                        .name(rs.getString("name"))
                        .type(ReferenceType.fromCode(rs.getInt("kind")))
                        .commitId(rs.getLong("commit_id"))
                        .build())
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to find all refs", e);
            return List.of();
        }
    }

    @Override
    public List<References> findByCommitId(Long commitId) {
        try {
            return dbManager.getJdbi().withHandle(handle ->
                handle.createQuery("SELECT name, kind, commit_id FROM ref WHERE commit_id = :commit_id")
                    .bind("commit_id", commitId)
                    .map((rs, ctx) -> References.builder()
                        .name(rs.getString("name"))
                        .type(ReferenceType.fromCode(rs.getInt("kind")))
                        .commitId(rs.getLong("commit_id"))
                        .build())
                    .list()
            );
        } catch (Exception e) {
            log.error("Failed to find refs by commit id: {}", commitId, e);
            return List.of();
        }
    }
}
