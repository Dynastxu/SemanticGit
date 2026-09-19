package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.ReferenceType;
import com.github.semanticgit.common.entity.References;
import com.github.semanticgit.core.dao.RefDao;
import com.github.semanticgit.core.db.DatabaseManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
        String sql = """
                    INSERT OR REPLACE INTO ref (name, kind, commit_id) VALUES (?, ?, ?)
                    """;
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ref.getName());
            ps.setInt(2, ref.getType().code);
            ps.setLong(3, ref.getCommitId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save ref: {}", ref.getName(), e);
        }
    }

    @Override
    public void saveAll(@NonNull List<References> refs) {
        if (refs.isEmpty()) {
            return;
        }
        try {
            Connection conn = dbManager.getConnection();
            conn.setAutoCommit(false);
            try {
                String sql = """
                            INSERT OR REPLACE INTO ref (name, kind, commit_id) VALUES (?, ?, ?)
                            """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (References ref : refs) {
                        ps.setString(1, ref.getName());
                        ps.setInt(2, ref.getType().code);
                        ps.setLong(3, ref.getCommitId());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                log.info("Saved {} refs to database", refs.size());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to save refs batch", e);
        }
    }

    @Override
    public Optional<References> findByName(String name) {
        String sql = "SELECT name, kind, commit_id FROM ref WHERE name = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRef(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find ref by name: {}", name, e);
        }
        return Optional.empty();
    }

    @Override
    public List<References> findAll() {
        List<References> result = new ArrayList<>();
        String sql = "SELECT name, kind, commit_id FROM ref";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRef(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find all refs", e);
        }
        return result;
    }

    @Override
    public List<References> findByCommitId(Long commitId) {
        List<References> result = new ArrayList<>();
        String sql = "SELECT name, kind, commit_id FROM ref WHERE commit_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, commitId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRef(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find refs by commit id: {}", commitId, e);
        }
        return result;
    }

    private References mapRef(ResultSet rs) throws SQLException {
        return References.builder()
                .name(rs.getString("name"))
                .type(ReferenceType.fromCode(rs.getInt("kind")))
                .commitId(rs.getLong("commit_id"))
                .build();
    }
}
