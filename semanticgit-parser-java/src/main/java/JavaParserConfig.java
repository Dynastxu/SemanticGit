import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class JavaParserConfig extends ParserConfig {
    /**
     * 超过此大小的文件，直接走文件级兜底，不尝试 AST 或正则
     */
    @Builder.Default
    private long maxParseSizeBytes = 5 * 1024 * 1024; // 5MB

    /**
     * 正则降级时允许的最大文件大小（正则扫描也消耗内存）
     */
    @Builder.Default
    private long maxRegexSizeBytes = 2 * 1024 * 1024; // 2MB
}
