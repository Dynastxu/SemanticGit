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
    private Integer timestamp;
    private String message;
}
