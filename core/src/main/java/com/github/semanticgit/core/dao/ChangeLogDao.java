package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.ChangeLog;

import java.util.List;

public interface ChangeLogDao {
    void save(ChangeLog changeLog);
    void saveAll(List<ChangeLog> changeLogs);
}
