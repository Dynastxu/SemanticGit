package com.github.semanticgit.git.dto;

import com.github.semanticgit.common.entity.ChangeOperation;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GitDiffEntry {
    private String oldPath;
    private String newPath;
    private ChangeOperation changeOperation;
    private String oldContent;
    private String newContent;
}
