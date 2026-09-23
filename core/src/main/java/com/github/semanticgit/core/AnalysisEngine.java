package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.RefDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.dao.impl.RefDaoImpl;
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
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.Constants;

@Slf4j
public class AnalysisEngine extends AbstractAnalysisEngine {

    @Override
    public CompletableFuture<Void> fullAnalysisAsync(String repoPath, String databaseDir, String databaseName, Consumer<Float> onProgress, Function<Throwable, Void> onError) {
        int hash = Objects.hash(repoPath, databaseDir, databaseName);

        return fullAnalysisFutures.compute(hash, (_, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            return CompletableFuture.runAsync(() -> {
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
            }).whenComplete((_, _) -> fullAnalysisFutures.remove(hash));
        });
    }

    private void runFullAnalysis(String repoPath, String databaseDir, String databaseName,
                                 Consumer<Float> onProgress) throws Exception {
        try (GitService gitService = new GitService(repoPath);
             DatabaseManager dbManager = new DatabaseManager(databaseDir, databaseName, true)) {

            List<CommitMeta> commits = gitService.getAllCommits();
            log.info("Fetched {} commits from repository", commits.size());

            CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
            List<CommitMeta> oldestFirst = new ArrayList<>(commits);
            Collections.reverse(oldestFirst);
            commitMetaDao.saveAll(oldestFirst);

            saveRefs(gitService, dbManager, commitMetaDao);

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
        List<ChangeLog> changeLogs = diffs.parallelStream()
                .flatMap(diff -> {
                    String filePath = diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath();
                    EntityLanguage language = ParserRegistry.detectLanguage(filePath);

                    if (language == null || !ParserRegistry.hasParser(language)) {
                        return Stream.empty();
                    }

                    SourceCode beforeCode = buildSourceCode(filePath, diff.getOldContent(), language);
                    SourceCode afterCode = buildSourceCode(filePath, diff.getNewContent(), language);

                    LanguageParser parser = ParserRegistry.getParserInstance(language);
                    List<EntityChange> entityChanges = parser.parseChangeNatureFlags(beforeCode, afterCode);

                    return entityChanges.stream().map(ec -> {
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

                        return ChangeLog.builder()
                                .commit(commit)
                                .entity(entity)
                                .filePath(filePath)
                                .operation(operation)
                                .natureFlagCode(ec.getFlags())
                                .parentEntity(parentEntity)
                                .dataQuality(DataQuality.AST)
                                .analysisType(AnalysisType.INCREMENTAL)
                                .build();
                    });
                })
                .collect(Collectors.toList());

        return matchCrossFileRefactors(changeLogs);
    }

    private static final double SIGNATURE_MATCH_THRESHOLD = 0.7;

    @NonNull List<ChangeLog> matchCrossFileRefactors(@NonNull List<ChangeLog> changeLogs) {
        List<ChangeLog> removals = changeLogs.stream()
                .filter(c -> c.getOperation() == ChangeOperation.REMOVE).toList();
        List<ChangeLog> additions = changeLogs.stream()
                .filter(c -> c.getOperation() == ChangeOperation.ADD).toList();

        Set<ChangeLog> matched = ConcurrentHashMap.newKeySet();
        List<ChangeLog> result = Collections.synchronizedList(new ArrayList<>());

        removals.parallelStream().forEach(removed -> {
            Entity removedEntity = removed.getEntity();
            for (ChangeLog added : additions) {
                if (removed.getFilePath().equals(added.getFilePath())) continue;

                Entity addedEntity = added.getEntity();
                if (removedEntity.getKind() != addedEntity.getKind()) continue;

                double similarity = computeStructuralSimilarity(removedEntity, addedEntity);
                if (similarity < SIGNATURE_MATCH_THRESHOLD) continue;

                if (!matched.add(added)) {
                    continue;
                }
                matched.add(removed);
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
                return;
            }
        });

        changeLogs.stream()
                .filter(c -> !matched.contains(c))
                .forEach(result::add);

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

    private void saveRefs(GitService gitService, DatabaseManager dbManager,
                           CommitMetaDao commitMetaDao) throws Exception {
        RefDao refDao = new RefDaoImpl(dbManager);
        Map<String, String> branchHeads = gitService.getAllBranchHeads();
        List<References> refs = new ArrayList<>();
        for (Map.Entry<String, String> entry : branchHeads.entrySet()) {
            CommitMeta headMeta = commitMetaDao.findByHash(entry.getValue());
            if (headMeta != null && headMeta.getId() != null) {
                refs.add(References.builder()
                        .name(Constants.R_HEADS + entry.getKey())
                        .type(ReferenceType.BRANCH)
                        .commitId(headMeta.getId())
                        .build());
            }
        }
        refDao.saveAll(refs);
        log.info("Saved {} branch refs", refs.size());
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
    public CompletableFuture<Void> incrementalAnalysisAsync(String repoPath, File databaseFile,
            Consumer<Float> onProgress, Function<Throwable, Void> onError) {
        int hash = Objects.hash(repoPath, databaseFile);
        if (incrementalAnalysisFutures.containsKey(hash)) {
            if (incrementalAnalysisFutures.get(hash).isDone()) {
                incrementalAnalysisFutures.remove(hash);
            } else {
                return incrementalAnalysisFutures.get(hash);
            }
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                runIncrementalAnalysis(repoPath, databaseFile, onProgress);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).exceptionally(e -> {
            log.error("Incremental analysis failed", e);
            if (onError != null) {
                onError.apply(e);
            }
            return null;
        }).whenComplete((_, _) -> incrementalAnalysisFutures.remove(hash));

        incrementalAnalysisFutures.put(hash, future);
        return future;
    }

    private void runIncrementalAnalysis(String repoPath, File databaseFile,
            Consumer<Float> onProgress) throws Exception {
        if (!databaseFile.exists()) {
            throw new IllegalStateException(
                    "Database file does not exist: " + databaseFile.getAbsolutePath()
                            + ". Please run full analysis first.");
        }

        try (GitService gitService = new GitService(repoPath);
             DatabaseManager dbManager = new DatabaseManager(databaseFile, false)) {

            CommitMetaDao commitMetaDao = new CommitMetaDaoImpl(dbManager);
            ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);

            CommitMeta latestInDb = commitMetaDao.findLatest();
            if (latestInDb == null) {
                throw new IllegalStateException(
                        "Database is empty. Please run full analysis first.");
            }

            String latestHash = latestInDb.getHash();
            String headHash = gitService.getHeadCommit();
            if (headHash == null) {
                log.info("HEAD is null, nothing to analyze");
                if (onProgress != null) {
                    onProgress.accept(1.0f);
                }
                return;
            }

            if (latestHash.equals(headHash)) {
                log.info("Already up to date, no new commits");
                if (onProgress != null) {
                    onProgress.accept(1.0f);
                }
                return;
            }

            List<GitCommitInfo> newCommitInfos;
            try {
                newCommitInfos = gitService.getCommitsBetween(latestHash, headHash);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Database commit " + latestHash.substring(0, 7)
                                + " is not an ancestor of HEAD. A full re-analysis may be required.",
                        e);
            }

            Set<String> existingHashes = commitMetaDao.findAll().stream()
                    .map(CommitMeta::getHash)
                    .collect(Collectors.toSet());

            newCommitInfos = newCommitInfos.stream()
                    .filter(info -> !existingHashes.contains(info.getCommitHash()))
                    .toList();

            if (newCommitInfos.isEmpty()) {
                log.info("No new commits to analyze");
                if (onProgress != null) {
                    onProgress.accept(1.0f);
                }
                return;
            }

            log.info("Found {} new commits to analyze", newCommitInfos.size());

            List<CommitMeta> newCommitMetas = newCommitInfos.stream()
                    .map(info -> CommitMeta.builder()
                            .hash(info.getCommitHash())
                            .author(Author.builder()
                                    .name(info.getAuthorName())
                                    .email(info.getAuthorEmail())
                                    .build())
                            .timestamp((int) info.getTimestamp())
                            .message(info.getFullMessage())
                            .build())
                    .toList();

            commitMetaDao.saveAll(newCommitMetas);
            saveRefs(gitService, dbManager, commitMetaDao);

            for (int i = 0; i < newCommitInfos.size(); i++) {
                GitCommitInfo info = newCommitInfos.get(i);
                CommitMeta meta = newCommitMetas.get(i);

                List<ChangeLog> changeLogs = analyzeDiff(info.getDiffEntries(), meta);
                changeLogDao.saveAll(changeLogs);

                if (onProgress != null) {
                    onProgress.accept((float) (i + 1) / newCommitInfos.size());
                }

                log.info("Analyzed commit {}/{}: {} changes",
                        i + 1, newCommitInfos.size(), changeLogs.size());
            }
        }
    }

    @Deprecated(forRemoval = true)
    public boolean isDatabaseExists(String repoPath, String databaseDir) {
        String dbName = Integer.toHexString(new File(repoPath).getAbsolutePath().hashCode());
        File dbFile = new File(databaseDir, dbName + ".db");
        return dbFile.exists();
    }
}