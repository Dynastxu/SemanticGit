package com.github.semanticgit.core;

import com.github.semanticgit.core.dto.CommitEntityChangeStatistics;
import com.github.semanticgit.core.dto.EntityChangeHistory;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;

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
}
