package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.ChangeLog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface ChangeLogDao {
    void save(ChangeLog changeLog);
    void saveAll(List<ChangeLog> changeLogs);
    ResultSet querySimpleEntityChangeStatistics(Connection conn) throws SQLException;
}
