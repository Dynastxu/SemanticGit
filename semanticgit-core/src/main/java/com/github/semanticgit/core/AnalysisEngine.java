package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.AnalysisType;
import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import com.github.semanticgit.git.service.GitService;
import com.github.semanticgit.parser.api.ParsingResult;
import com.github.semanticgit.parser.api.SourceCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class AnalysisEngine {

    public boolean fullAnalysis(String repoPath, String databasePath) {
        String repoName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());

        try (GitService gitService = new GitService(repoPath);
             DatabaseManager dbManager = new DatabaseManager(databasePath, repoName, true)) {
            List<CommitMeta> commits = gitService.getAllCommits();
            log.info("Fetched {} commits from repository", commits.size());

            CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
            commitMetaDao.saveAll(commits);

            if (commits.isEmpty()) {
                return true;
            }

            List<CommitMeta> chronological = new ArrayList<>(commits);
            Collections.reverse(chronological);

            String firstHash = chronological.getFirst().getHash();
            String lastHash = chronological.getLast().getHash();

            Map<String, CommitMeta> commitMetaMap = commits.stream()
                    .collect(Collectors.toMap(CommitMeta::getHash, c -> c));

            List<GitCommitInfo> commitInfos;
            if (firstHash.equals(lastHash)) {
                GitCommitInfo info = gitService.getCommitInfo(firstHash);
                commitInfos = Collections.singletonList(info);
            } else {
                commitInfos = gitService.getCommitsBetween(firstHash, lastHash);
            }

            ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);
            ParserRegistry parserRegistry = new ParserRegistry();

            for (int i = 0; i < commitInfos.size(); i++) {
                GitCommitInfo info = commitInfos.get(i);
                CommitMeta commitMeta = commitMetaMap.get(info.getCommitHash());
                if (commitMeta == null) {
                    log.warn("Commit {} not found in commit meta map",
                            info.getCommitHash().substring(0, Math.min(7, info.getCommitHash().length())));
                    continue;
                }

                List<ChangeLog> changeLogs = analyzeDiff(info.getDiffEntries(), commitMeta, parserRegistry);
                changeLogDao.saveAll(changeLogs);

                log.info("Analyzed commit {}/{}: {} changes", i + 1, commitInfos.size(), changeLogs.size());
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to perform full analysis", e);
            return false;
        }
    }

    private List<ChangeLog> analyzeDiff(List<GitDiffEntry> diffs, CommitMeta commit, ParserRegistry parserRegistry) {
        List<ChangeLog> changeLogs = new ArrayList<>();

        for (GitDiffEntry diff : diffs) {
            String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
            EntityLanguage language = ParserRegistry.detectLanguage(filePath);

            if (language == null) {
                continue;
            }

            switch (diff.getChangeOperation()) {
                case ADD -> {
                    ParsingResult newResult = parseFile(diff.getNewContent(), filePath, language, parserRegistry);
                    for (Entity entity : newResult.getEntities()) {
                        entity.setLanguage(language);
                        changeLogs.add(buildChangeLog(commit, entity, filePath,
                                ChangeOperation.ADD, newResult));
                    }
                }
                case REMOVE -> {
                    ParsingResult oldResult = parseFile(diff.getOldContent(), filePath, language, parserRegistry);
                    for (Entity entity : oldResult.getEntities()) {
                        entity.setLanguage(language);
                        changeLogs.add(buildChangeLog(commit, entity, filePath,
                                ChangeOperation.REMOVE, oldResult));
                    }
                }
                case MODIFY -> {
                    ParsingResult oldResult = parseFile(diff.getOldContent(), filePath, language, parserRegistry);
                    ParsingResult newResult = parseFile(diff.getNewContent(), filePath, language, parserRegistry);

                    Set<String> oldNames = entityNameSet(oldResult);
                    Set<String> newNames = entityNameSet(newResult);

                    for (Entity entity : oldResult.getEntities()) {
                        entity.setLanguage(language);
                        if (!newNames.contains(entity.getName())) {
                            changeLogs.add(buildChangeLog(commit, entity, filePath,
                                    ChangeOperation.REMOVE, oldResult));
                        }
                    }

                    for (Entity entity : newResult.getEntities()) {
                        entity.setLanguage(language);
                        if (!oldNames.contains(entity.getName())) {
                            changeLogs.add(buildChangeLog(commit, entity, filePath,
                                    ChangeOperation.ADD, newResult));
                        } else {
                            changeLogs.add(buildChangeLog(commit, entity, filePath,
                                    ChangeOperation.MODIFY, newResult));
                        }
                    }
                }
            }
        }

        return changeLogs;
    }

    private ParsingResult parseFile(String content, String filePath, EntityLanguage language,
                                     ParserRegistry parserRegistry) {
        if (content == null || content.isEmpty()) {
            return ParsingResult.builder()
                    .entities(Collections.emptyList())
                    .quality(DataQuality.FILE)
                    .qualityRemark("EMPTY_CONTENT")
                    .parseDurationMs(0)
                    .build();
        }

        SourceCode sourceCode = SourceCode.builder()
                .filePath(filePath)
                .content(content)
                .language(language)
                .sizeInBytes(content.getBytes().length)
                .build();

        return parserRegistry.parse(sourceCode);
    }

    private Set<String> entityNameSet(ParsingResult result) {
        return result.getEntities().stream()
                .map(Entity::getName)
                .collect(Collectors.toSet());
    }

    private ChangeLog buildChangeLog(CommitMeta commit, Entity entity, String filePath,
                                      ChangeOperation operation, ParsingResult result) {
        return ChangeLog.builder()
                .commit(commit)
                .entity(entity)
                .filePath(filePath)
                .operation(operation)
                .natureFlag(ChangeNatureFlag.LOGICAL)
                .dataQuality(result.getQuality())
                .analysisType(AnalysisType.INCREMENTAL)
                .build();
    }
}
