package com.github.semanticgit.parser.java.api;

import com.github.semanticgit.common.entity.Entity;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EntityChange {
    /**
     * 变更前实体，新增时为 null
     */
    Entity before;
    /**
     * 变更后实体，删除时为 null
     */
    Entity after;
    /**
     * 变更性质标记位掩码
     */
    int flags;
}
