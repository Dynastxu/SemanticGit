package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.parser.ParserRegistry;
import com.github.semanticgit.git.dto.GitCommitInfo;
import com.github.semanticgit.git.dto.GitDiffEntry;
import com.github.semanticgit.git.service.GitService;
import com.github.semanticgit.parser.java.api.EntityChange;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.SourceCode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class AnalysisEngine extends AbstractAnalysisEngine {
    @Override
    @Deprecated(forRemoval = true)
    public boolean fullAnalysis(String repoPath, String databaseDir) {
        return false;
    }

    @Override
    public CompletableFuture<Void> fullAnalysisAsync(String repoPath, String databaseDir, String databaseName, Consumer<Float> onProgress, Function<Throwable, Void> onError) {
        int hash = Objects.hash(repoPath, databaseDir, databaseName);
        if (fullAnalysisFutures.containsKey(hash)) {
            if (fullAnalysisFutures.get(hash).isDone()) {
                fullAnalysisFutures.remove(hash);
            } else {
                return fullAnalysisFutures.get(hash);
            }
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                runFullAnalysis(repoPath, databaseDir, databaseName, onProgress);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).exceptionally(e -> {
            log.error("Full analysis failed", e);
            if (onError != null) {
                onError.apply(e);
            }
            return null;
        });

        fullAnalysisFutures.put(hash, future);
        return future;

    }

    private void runFullAnalysis(String repoPath, String databaseDir, String databaseName,
                                 Consumer<Float> onProgress) throws Exception {
        try (GitService gitService = new GitService(repoPath);
             DatabaseManager dbManager = new DatabaseManager(databaseDir, databaseName, true)) {

            List<CommitMeta> commits = gitService.getAllCommits();
            log.info("Fetched {} commits from repository", commits.size());

            CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
            commitMetaDao.saveAll(commits);

            if (commits.isEmpty()) {
                return;
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

                if (onProgress != null) {
                    float progress = (float) (i + 1) / commitInfos.size();
                    onProgress.accept(progress);
                }

                log.info("Analyzed commit {}/{}: {} changes", i + 1, commitInfos.size(), changeLogs.size());
            }
        }
    }

    private @NonNull List<ChangeLog> analyzeDiff(@NonNull List<GitDiffEntry> diffs, CommitMeta commit) {
        List<ChangeLog> changeLogs = new ArrayList<>();

        for (GitDiffEntry diff : diffs) {
            String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
            EntityLanguage language = ParserRegistry.detectLanguage(filePath);

            if (language == null || !ParserRegistry.hasParser(language)) {
                continue;
            }

            SourceCode beforeCode = buildSourceCode(filePath, diff.getOldContent(), language);
            SourceCode afterCode = buildSourceCode(filePath, diff.getNewContent(), language);

            LanguageParser<?> parser = ParserRegistry.getParser(language);
            List<EntityChange> entityChanges = parser.parseChangeNatureFlags(beforeCode, afterCode);

            for (EntityChange ec : entityChanges) {
                Entity entity;
                ChangeOperation operation;
                Entity parentEntity = null;

                if (ec.getBefore() == null) {
                    entity = ec.getAfter();
                    operation = ChangeOperation.ADD;
                } else if (ec.getAfter() == null) {
                    entity = ec.getBefore();
                    operation = ChangeOperation.REMOVE;
                } else {
                    entity = ec.getAfter();
                    operation = ChangeOperation.MODIFY;
                    if (!ec.getBefore().getName().equals(ec.getAfter().getName())) {
                        parentEntity = ec.getBefore();
                    }
                }

                entity.setLanguage(language);

                changeLogs.add(ChangeLog.builder()
                        .commit(commit)
                        .entity(entity)
                        .filePath(filePath)
                        .operation(operation)
                        .natureFlagCode(ec.getFlags())
                        .parentEntity(parentEntity)
                        .dataQuality(DataQuality.AST)
                        .analysisType(AnalysisType.INCREMENTAL)
                        .build());
            }
        }

        changeLogs = matchCrossFileRefactors(changeLogs);
        return changeLogs;
    }

    private static final double SIGNATURE_MATCH_THRESHOLD = 0.7;

    @NonNull List<ChangeLog> matchCrossFileRefactors(@NonNull List<ChangeLog> changeLogs) {
        List<ChangeLog> removals = changeLogs.stream()
                .filter(c -> c.getOperation() == ChangeOperation.REMOVE).toList();
        List<ChangeLog> additions = changeLogs.stream()
                .filter(c -> c.getOperation() == ChangeOperation.ADD).toList();

        Set<ChangeLog> matched = new HashSet<>();
        List<ChangeLog> result = new ArrayList<>();

        for (ChangeLog removed : removals) {
            Entity removedEntity = removed.getEntity();
            for (ChangeLog added : additions) {
                if (matched.contains(added)) continue;
                if (removed.getFilePath().equals(added.getFilePath())) continue;

                Entity addedEntity = added.getEntity();
                if (removedEntity.getKind() != addedEntity.getKind()) continue;

                double similarity = computeStructuralSimilarity(removedEntity, addedEntity);
                if (similarity < SIGNATURE_MATCH_THRESHOLD) continue;

                matched.add(removed);
                matched.add(added);
                result.add(ChangeLog.builder()
                        .commit(added.getCommit())
                        .entity(addedEntity)
                        .filePath(added.getFilePath())
                        .operation(ChangeOperation.MODIFY)
                        .natureFlagCode(added.getNatureFlagCode() | ChangeNatureFlag.REFACTOR.code)
                        .parentEntity(removedEntity)
                        .dataQuality(added.getDataQuality())
                        .analysisType(added.getAnalysisType())
                        .build());
                break;
            }
        }

        for (ChangeLog c : changeLogs) {
            if (!matched.contains(c)) {
                result.add(c);
            }
        }
        return result;
    }

    private double computeStructuralSimilarity(@NonNull Entity removed, @NonNull Entity added) {
        String sig1 = removed.getSignature();
        String sig2 = added.getSignature();

        if (sig1 == null || sig2 == null || sig1.isEmpty() || sig2.isEmpty()) {
            return 0.0;
        }

        if (removed.getKind() == EntityKind.METHOD) {
            return computeMethodSimilarity(sig1, sig2);
        }
        if (removed.getKind() == EntityKind.CLASS) {
            return computeClassSimilarity(sig1, sig2);
        }
        return 0.0;
    }

    private double computeMethodSimilarity(@NonNull String sig1, @NonNull String sig2) {
        String[] parts1 = sig1.split(":", 3);
        String[] parts2 = sig2.split(":", 3);

        if (parts1.length < 2 || parts2.length < 2) {
            return 0.0;
        }

        if (!parts1[0].equals(parts2[0]) || !parts1[1].equals(parts2[1])) {
            return 0.0;
        }

        if (parts1.length == 3 && parts2.length == 3 && parts1[2].equals(parts2[2])) {
            return 1.0;
        }

        return 0.7;
    }

    private double computeClassSimilarity(@NonNull String sig1, @NonNull String sig2) {
        String[] parts1 = sig1.split(":", 4);
        String[] parts2 = sig2.split(":", 4);

        if (parts1.length < 4 || parts2.length < 4) {
            return 0.0;
        }

        if (!parts1[0].equals(parts2[0]) || !parts1[1].equals(parts2[1])) {
            return 0.0;
        }

        if (!parts1[2].equals(parts2[2])) {
            return 0.3;
        }

        Set<String> methods1 = new HashSet<>(Arrays.asList(parts1[3].split(";")));
        Set<String> methods2 = new HashSet<>(Arrays.asList(parts2[3].split(";")));
        methods1.remove("");
        methods2.remove("");

        Set<String> intersection = new HashSet<>(methods1);
        intersection.retainAll(methods2);
        Set<String> union = new HashSet<>(methods1);
        union.addAll(methods2);

        if (union.isEmpty()) {
            return 0.5;
        }
        return (double) intersection.size() / union.size();
    }

    private SourceCode buildSourceCode(String filePath, String content, EntityLanguage language) {
        if (content == null) {
            return null;
        }
        return SourceCode.builder()
                .filePath(filePath)
                .content(content)
                .language(language)
                .sizeInBytes(content.length())
                .build();
    }


    @Override
    public CompletableFuture<Void> incrementalAnalysisAsync(String repoPath, File databaseFile, Consumer<Float> onProgress, Function<Throwable, Void> onError) {
        int hash = Objects.hash(repoPath, databaseFile);
        if (incrementalAnalysisFutures.containsKey(hash)) {
            if (incrementalAnalysisFutures.get(hash).isDone()) {
                incrementalAnalysisFutures.remove(hash);
            } else {
                return incrementalAnalysisFutures.get(hash);
            }
        }
        // TODO 实现
        return null;
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean isDatabaseExists(String repoPath, String databaseDir) {
        String dbName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());
        File dbFile = new File(databaseDir, dbName + ".db");
        return dbFile.exists();
    }
}
