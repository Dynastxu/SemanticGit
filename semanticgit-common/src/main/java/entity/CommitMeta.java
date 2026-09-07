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
public class CommitMeta {
    private Long id;
    private String hash;
    private Long authorId;
    private Integer timestamp;
    private String message;

    public static class CommitMetaBuilder {
        public CommitMetaBuilder authorId(@NonNull Author author) {
            this.authorId = author.getId();
            return this;
        }
    }
}
