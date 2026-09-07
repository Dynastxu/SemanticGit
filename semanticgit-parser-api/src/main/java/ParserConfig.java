import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ParserConfig {
    /**
     * 单文件最大解析超时（毫秒）
     */
    @Builder.Default
    private long timeoutMs = 5000;
}
