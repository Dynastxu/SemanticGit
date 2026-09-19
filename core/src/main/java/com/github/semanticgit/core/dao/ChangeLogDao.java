package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface ChangeLogDao {
    void save(ChangeLog changeLog);
    void saveAll(List<ChangeLog> changeLogs);
    ResultSet querySimpleEntityChangeStatistics() throws SQLException;
    ResultSet queryCommitEntityChangeStatistics(String hash) throws SQLException;
    EntityChangeHistory queryEntityChangeHistory(String entityName, String refName);
    List<Entity> findAllEntities();
    AuthorChangeStatistics queryAuthorChangeStatistics(Author author, String refName);
}
