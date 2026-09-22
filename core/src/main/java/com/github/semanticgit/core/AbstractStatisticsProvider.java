package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.core.dto.AuthorChangeStatistics;
import com.github.semanticgit.core.dto.CommitEntityChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 统计提供提供器抽象类。
 * @apiNote 所有数据获取应当默认懒加载，外键引用仅记录 id。
 */
abstract class AbstractStatisticsProvider {
    public abstract SimpleEntityChangeStatistics getSimpleEntityChangeStatistics();

    public abstract CommitEntityChangeStatistics getCommitEntityChangeStatistics(String hash);

    /**
     * Get the entity change history.
     * @param entityName 实体全限定名
     * @param refName    ref全名（如 "refs/heads/main"），从此ref的HEAD向父提交遍历
     * @return 实体变更历史记录
     */
    public abstract EntityChangeHistory getEntityChangeHistory(String entityName, String refName);

    /**
     * Get the author change statistics.
     * @param author 作者
     * @param refName ref 全名，为 null 时统计所有提交
     * @return 统计数据
     */
    public abstract AuthorChangeStatistics getAuthorChangeStatistics(Author author, @Nullable String refName);

    public abstract List<Author> getAuthors();

    /**
     * Get all entities.
     * @return 实体列表。
     */
    public abstract List<Entity> getEntities();

    /**
     * Get all commits.
     * @return 提交元数据列表。
     */
    public abstract List<CommitMeta> getCommits();
}
