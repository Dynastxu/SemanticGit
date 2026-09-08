package dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GitCommitInfo {
    private String commitHash;
    private String authorName;
    private String authorEmail;
    private long timestamp;
    private String fullMessage;
    private List<GitDiffEntry> diffEntries;
}
