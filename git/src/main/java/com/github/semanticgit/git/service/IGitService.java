package com.github.semanticgit.git.service;

import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.git.dto.GitCommitInfo;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

interface IGitService {
    /**
     * 按分支取提交列表（时间倒序，最新在前）。
     *
     * @param branch 短名（"main"）、完整 ref（"refs/heads/main"）或 null（等价 HEAD）
     */
    List<CommitMeta> getAllCommits(String branch) throws IOException;

    /**
     * @return 当前 HEAD 指向的 commit hash，空仓库返回 null
     */
    @Nullable String getHeadCommit() throws IOException;

    /**
     * @return 所有本地分支 head。key=短名，value=commit hash
     */
    Map<String, String> getAllBranchHeads() throws IOException;

    /**
     * 获取提交信息。
     * @param revstr 一个 git 对象引用表达式
     * @return 提交信息
     * @see org.eclipse.jgit.lib.Repository#resolve(String)
     */
    GitCommitInfo getCommitInfo(String revstr) throws IOException;

    /**
     * 统计两个 ref 之间的提交数量。
     * @param fromRef 起始 ref
     * @param toRef  结束 ref
     * @return 提交数量
     */
    int countCommitsBetween(String fromRef, String toRef) throws IOException;

    /**
     * 遍历两个 ref 之间的所有提交。
     * @param fromRef 起始 ref
     * @param toRef  结束 ref
     * @param consumer 提交信息消费函数
     */
    void forEachCommitBetween(String fromRef, String toRef, Consumer<GitCommitInfo> consumer) throws IOException;
}
