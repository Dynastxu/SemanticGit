import entity.EntityLanguage;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SourceCode {
    /**
     * 文件在仓库中的相对路径（如 "src/main/java/Hello.java"）
     */
    private String filePath;

    /**
     * 文件内容
     */
    private String content;

    private EntityLanguage language;

    /**
     * 文件大小（字节）
     */
    private long sizeInBytes;
}
