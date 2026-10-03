package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import com.github.semanticgit.core.dto.StatRow;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface ChangeLogDao {
    enum TraversalMode {
        DFS,
        BFS
    }

    void save(ChangeLog changeLog);
    void saveAll(List<ChangeLog> changeLogs);
    List<StatRow> querySimpleEntityChangeStatistics();
    List<StatRow> queryCommitEntityChangeStatistics(String hash);
    EntityChangeHistory queryEntityChangeHistory(String entityName, @Nullable String refName, TraversalMode mode);

    default EntityChangeHistory queryEntityChangeHistory(String entityName, @Nullable String refName) {
        return queryEntityChangeHistory(entityName, refName, TraversalMode.DFS);
    }

    List<Entity> findAllEntities();
    AuthorChangeStatistics queryAuthorChangeStatistics(Author author, String refName);
}
