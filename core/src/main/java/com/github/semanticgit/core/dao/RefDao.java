package com.github.semanticgit.core.dao;

import com.github.semanticgit.common.entity.References;

import java.util.List;
import java.util.Optional;

public interface RefDao {
    void save(References ref);
    void saveAll(List<References> refs);
    Optional<References> findByName(String name);
    List<References> findAll();
    List<References> findByCommitId(Long commitId);
}
