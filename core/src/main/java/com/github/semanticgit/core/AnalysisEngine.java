package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.AnalysisType;
import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.parser.ParserRegistry;
import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import com.github.semanticgit.git.service.GitService;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.SourceCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class AnalysisEngine extends AbstractAnalysisEngine {
    @Getter
    private String failReason = "";
    @Getter
    private String failMessage = "";

    @Override
    public boolean fullAnalysis(String repoPath, String databaseDir) {
        String dbName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());

        try (GitService gitService = new GitService(repoPath);
             DatabaseManager dbManager = new DatabaseManager(databaseDir, dbName, true)) {
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

            for (int i = 0; i < commitInfos.size(); i++) {
                GitCommitInfo info = commitInfos.get(i);
                CommitMeta commitMeta = commitMetaMap.get(info.getCommitHash());
                if (commitMeta == null) {
                    log.warn("Commit {} not found in commit meta map",
                            info.getCommitHash().substring(0, Math.min(7, info.getCommitHash().length())));
                    continue;
                }

                List<ChangeLog> changeLogs = analyzeDiff(info.getDiffEntries(), commitMeta);
                changeLogDao.saveAll(changeLogs);

                log.info("Analyzed commit {}/{}: {} changes", i + 1, commitInfos.size(), changeLogs.size());
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to perform full analysis", e);
            failMessage = e.toString();
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> fullAnalysisAsync(String repoPath, String databaseDir, String databaseName, Callable<Float> onProgress, Function<Throwable, Void> onError) {
        if (fullAnalysisFuture != null && fullAnalysisFuture.isDone()) {
            return fullAnalysisFuture;
        }
        // TODO 实现
        return null;
    }

    @Override
    public CompletableFuture<Void> incrementalAnalysisAsync(String repoPath, File databaseFile, Callable<Float> onProgress, Function<Throwable, Void> onError) {
        if (incrementalAnalysisFuture != null && incrementalAnalysisFuture.isDone()) {
            return incrementalAnalysisFuture;
        }
        // TODO 实现
        return null;
    }

    @Override
    public boolean isDatabaseExists(String repoPath, String databaseDir) {
        String dbName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());
        File dbFile = new File(databaseDir, dbName + ".db");
        return dbFile.exists();
    }

    private @NonNull List<ChangeLog> analyzeDiff(@NonNull List<GitDiffEntry> diffs, CommitMeta commit) {
        List<ChangeLog> changeLogs = new ArrayList<>();

        for (GitDiffEntry diff : diffs) {
            String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
            EntityLanguage language = ParserRegistry.detectLanguage(filePath);

            if (language == null || !ParserRegistry.hasParser(language)) {
                continue;
            }

            switch (diff.getChangeOperation()) {
                case ADD -> {
                    SourceCode newCode = buildSourceCode(filePath, diff.getNewContent(), language);
                    LanguageParser<?> parser = ParserRegistry.getParser(language);
                    ParsingResult result = parser.parseEntities(newCode);

                    for (Entity entity : result.getEntities()) {
                        entity.setLanguage(language);
                        int entityFlagCode = parser.parseChangeNatureFlag(null, newCode, entity.getName());
                        changeLogs.add(ChangeLog.builder()
                                .commit(commit)
                                .entity(entity)
                                .filePath(filePath)
                                .operation(ChangeOperation.ADD)
                                .natureFlagCode(entityFlagCode)
                                .dataQuality(result.getQuality())
                                .analysisType(AnalysisType.INCREMENTAL)
                                .build());
                    }
                }
                case REMOVE -> {
                    SourceCode oldCode = buildSourceCode(filePath, diff.getOldContent(), language);
                    LanguageParser<?> parser = ParserRegistry.getParser(language);
                    ParsingResult result = parser.parseEntities(oldCode);

                    for (Entity entity : result.getEntities()) {
                        entity.setLanguage(language);
                        int entityFlagCode = parser.parseChangeNatureFlag(oldCode, null, entity.getName());
                        changeLogs.add(ChangeLog.builder()
                                .commit(commit)
                                .entity(entity)
                                .filePath(filePath)
                                .operation(ChangeOperation.REMOVE)
                                .natureFlagCode(entityFlagCode)
                                .dataQuality(result.getQuality())
                                .analysisType(AnalysisType.INCREMENTAL)
                                .build());
                    }
                }
                case MODIFY -> {
                    SourceCode oldCode = buildSourceCode(filePath, diff.getOldContent(), language);
                    SourceCode newCode = buildSourceCode(filePath, diff.getNewContent(), language);
                    LanguageParser<?> parser = ParserRegistry.getParser(language);

                    ParsingResult oldResult = parser.parseEntities(oldCode);
                    ParsingResult newResult = parser.parseEntities(newCode);

                    Map<String, Entity> oldEntityMap = oldResult.getEntities().stream()
                            .collect(Collectors.toMap(Entity::getName, e -> e, (a, _) -> a));
                    Map<String, Entity> newEntityMap = newResult.getEntities().stream()
                            .collect(Collectors.toMap(Entity::getName, e -> e, (a, _) -> a));

                    for (Map.Entry<String, Entity> entry : newEntityMap.entrySet()) {
                        Entity entity = entry.getValue();
                        entity.setLanguage(language);
                        int entityFlagCode = parser.parseChangeNatureFlag(oldCode, newCode, entity.getName());

                        if (oldEntityMap.containsKey(entry.getKey())) {
                            changeLogs.add(ChangeLog.builder()
                                    .commit(commit)
                                    .entity(entity)
                                    .filePath(filePath)
                                    .operation(ChangeOperation.MODIFY)
                                    .natureFlagCode(entityFlagCode)
                                    .dataQuality(newResult.getQuality())
                                    .analysisType(AnalysisType.INCREMENTAL)
                                    .build());
                        } else {
                            changeLogs.add(ChangeLog.builder()
                                    .commit(commit)
                                    .entity(entity)
                                    .filePath(filePath)
                                    .operation(ChangeOperation.ADD)
                                    .natureFlagCode(entityFlagCode)
                                    .dataQuality(newResult.getQuality())
                                    .analysisType(AnalysisType.INCREMENTAL)
                                    .build());
                        }
                    }

                    for (Map.Entry<String, Entity> entry : oldEntityMap.entrySet()) {
                        if (!newEntityMap.containsKey(entry.getKey())) {
                            Entity entity = entry.getValue();
                            entity.setLanguage(language);
                            int entityFlagCode = parser.parseChangeNatureFlag(oldCode, newCode, entity.getName());
                            changeLogs.add(ChangeLog.builder()
                                    .commit(commit)
                                    .entity(entity)
                                    .filePath(filePath)
                                    .operation(ChangeOperation.REMOVE)
                                    .natureFlagCode(entityFlagCode)
                                    .dataQuality(oldResult.getQuality())
                                    .analysisType(AnalysisType.INCREMENTAL)
                                    .build());
                        }
                    }
                }
            }
        }

        return changeLogs;
    }

    private SourceCode buildSourceCode(String filePath, String content, EntityLanguage language) {
        return SourceCode.builder()
                .filePath(filePath)
                .content(content)
                .language(language)
                .sizeInBytes(content != null ? content.length() : 0)
                .build();
    }
}
