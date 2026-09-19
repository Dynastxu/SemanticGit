package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import com.github.semanticgit.core.dto.StatRow;

import java.util.List;

public interface ChangeLogDao {
    void save(ChangeLog changeLog);
    void saveAll(List<ChangeLog> changeLogs);
    List<StatRow> querySimpleEntityChangeStatistics();
    List<StatRow> queryCommitEntityChangeStatistics(String hash);
    EntityChangeHistory queryEntityChangeHistory(String entityName, String refName);
    List<Entity> findAllEntities();
    AuthorChangeStatistics queryAuthorChangeStatistics(Author author, String refName);
}
