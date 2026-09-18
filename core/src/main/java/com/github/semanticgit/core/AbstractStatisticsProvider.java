package com.github.semanticgit.core;

import com.github.semanticgit.core.dto.CommitEntityChangeStatistics;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;

abstract class AbstractStatisticsProvider {
    public abstract SimpleEntityChangeStatistics getSimpleEntityChangeStatistics();

    public abstract CommitEntityChangeStatistics getCommitEntityChangeStatistics(String hash);
}
