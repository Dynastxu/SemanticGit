package entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLog {
    private Long id;
    private Long commitId;
    private Long entityId;
    private String filePath;
    private ChangeOperation operation;
    private ChangeNatureFlag natureFlag;
    /**
     * 重构时指向旧实体
     */
    private Long parentId;
    private DataQuality dataQuality;
    private AnalysisType analysisType;

    public static class ChangeLogBuilder {
        public ChangeLogBuilder commitId(@NonNull CommitMeta commitMeta) {
            this.commitId = commitMeta.getId();
            return this;
        }

        public ChangeLogBuilder entityId(@NonNull Entity entity) {
            this.entityId = entity.getId();
            return this;
        }

        public ChangeLogBuilder parentId(@NonNull Entity entity) {
            this.parentId = entity.getParentId();
            return this;
        }
    }
}
