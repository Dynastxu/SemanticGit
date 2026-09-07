import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParserConfig {
    /**
     * 单文件最大解析超时（毫秒）
     */
    @Builder.Default
    private long timeoutMs = 5000;
    /**
     * 是否解析方法内部
     */
    @Builder.Default
    private boolean enableMethodBodyAnalysis = true;
    /**
     * 是否强制校验 JDK 版本兼容性
     */
    @Builder.Default
    private boolean strictJavaVersionCheck = false;
}
