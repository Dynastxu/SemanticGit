package com.github.semanticgit.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitMeta {
    private Long id;
    private String hash;
    private Author author;
    private Integer timestamp; // FIXME 改为 Long 类型
    private String message;
    private CommitMeta parentCommitMeta;
}
