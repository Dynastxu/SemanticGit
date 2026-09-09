package com.github.semanticgit.common.entity;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class References {
    private Long id;
    private String name;
    private ReferenceType type;
    private CommitMeta commit;
}
