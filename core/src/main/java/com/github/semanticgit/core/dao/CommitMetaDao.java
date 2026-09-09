package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.CommitMeta;

import java.util.List;

public interface CommitMetaDao {
    void save(CommitMeta commit);
    void saveAll(List<CommitMeta> commits);
}
