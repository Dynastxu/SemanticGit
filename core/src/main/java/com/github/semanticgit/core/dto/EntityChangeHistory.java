package com.github.semanticgit.core.dto;

import com.github.semanticgit.common.entity.ChangeLog;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
public class EntityChangeHistory {
    @Builder.Default
    private List<ChangeLog> changes = new ArrayList<>();
}
