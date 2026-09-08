package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.git.service.GitService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
public class AnalysisEngine {
    public boolean fullAnalysis(String repoPath, String databasePath) {
        String repoName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());

        try (GitService gitService = new GitService(repoPath);
             DatabaseManager dbManager = new DatabaseManager(databasePath, repoName, true)) {
            // 所有提交及作者
            List<CommitMeta> commits = gitService.getAllCommits();
            log.info("Fetched {} commits from repository", commits.size());
            CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
            commitMetaDao.saveAll(commits);

            return true;
        } catch (Exception e) {
            log.error("Failed to perform full analysis", e);
            return false;
        }
    }
}
