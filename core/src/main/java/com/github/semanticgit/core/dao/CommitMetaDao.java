package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.CommitMeta;

import java.sql.SQLException;
import java.util.List;

public interface CommitMetaDao {
    void save(CommitMeta commit);
    void saveAll(List<CommitMeta> commits);
    CommitMeta findByHash(String hash) throws SQLException;
    List<CommitMeta> findAll();
    List<Author> findAllAuthors();
}
