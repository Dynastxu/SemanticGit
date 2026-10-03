package com.github.semanticgit.core.dto;

import com.github.semanticgit.common.entity.ChangeLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityChangeHistory {
    @Builder.Default
    private List<ChangeLog> changes = new ArrayList<>();
    @Builder.Default
    private List<HistoryNode> nodes = new ArrayList<>();
    @Builder.Default
    private List<HistoryEdge> edges = new ArrayList<>();
}