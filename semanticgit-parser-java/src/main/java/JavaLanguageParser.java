import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import entity.DataQuality;
import entity.Entity;
import entity.EntityKind;
import entity.EntityLanguage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
public class JavaLanguageParser implements LanguageParser {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public ParsingResult parse(@NonNull SourceCode sourceCode, ParserConfig config) {
        // 使用超时机制，防止解析卡死
        Future<ParsingResult> future = executor.submit(() -> {
            try {
                return doParse(sourceCode, config);
            } catch (Exception e) {
                log.error("Parse error for {}: {}", sourceCode.getFilePath(), e.getMessage());
                return buildFallbackResult(DataQuality.FILE, "CRASHED: " + e.getClass().getSimpleName());
            }
        });

        try {
            return future.get(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Parse timeout for {}: {}ms", sourceCode.getFilePath(), config.getTimeoutMs());
            return buildFallbackResult(DataQuality.FILE, "TIMEOUT");
        } catch (Exception e) {
            log.error("Unexpected parse error for {}: {}", sourceCode.getFilePath(), e.getMessage());
            return buildFallbackResult(DataQuality.FILE, "UNEXPECTED: " + e.getMessage());
        }
    }

    @Override
    public EntityLanguage getSupportedLanguage() {
        return EntityLanguage.JAVA;
    }

    /**
     * 核心解析逻辑（三级容错）
     */
    private ParsingResult doParse(@NonNull SourceCode sourceCode, ParserConfig config) {
        long startTime = System.currentTimeMillis();
        String content = sourceCode.getContent();

        // 空文件直接返回文件级结果
        if (content == null || content.trim().isEmpty()) {
            return buildFallbackResult(DataQuality.FILE, "EMPTY_FILE");
        }

        // === Level 1: 尝试 AST 完美解析 ===
        ParseResult<CompilationUnit> parseResult = parseWithAST(content, config);

        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();
            List<Entity> entities = extractEntities(cu);

            if (!entities.isEmpty()) {
                // 完美解析成功
                return ParsingResult.builder()
                        .entities(entities)
                        .quality(DataQuality.AST)
                        .qualityRemark("AST_SUCCESS")
                        .parseDurationMs(System.currentTimeMillis() - startTime)
                        .build();
            } else {
                // AST 解析成功但未提取到实体（可能是纯接口或空类）
                return buildFallbackResult(DataQuality.REGEXP, "AST_NO_ENTITIES");
            }
        }

        // === Level 2: 正则回退（AST 部分失败） ===
        List<Entity> regexEntities = extractWithRegex(content, sourceCode.getFilePath());
        if (!regexEntities.isEmpty()) {
            return ParsingResult.builder()
                    .entities(regexEntities)
                    .quality(DataQuality.REGEXP)
                    .qualityRemark("REGEX_FALLBACK" + (parseResult.getProblems().isEmpty() ? "" : ": " + parseResult.getProblems().get(0).getMessage()))
                    .parseDurationMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // === Level 3: 文件级兜底 ===
        return buildFallbackResult(DataQuality.FILE, "NO_ENTITIES_FOUND");
    }

    /**
     * Level 1: 使用 JavaParser 进行 AST 解析
     */
    private ParseResult<CompilationUnit> parseWithAST(String content, ParserConfig config) {
        ParserConfiguration parserConfig = new ParserConfiguration();
        parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17); // 可配置化
        parserConfig.setStoreTokens(true); // 便于调试

        JavaParser parser = new JavaParser(parserConfig);
        return parser.parse(ParseStart.COMPILATION_UNIT, new StringProvider(content));
    }

    /**
     * Level 2: 正则表达式提取（降级方案）
     * 仅提取类名和方法签名，不关心方法体是否正确
     */
    private @NonNull List<Entity> extractWithRegex(String content, String filePath) {
        List<Entity> entities = new ArrayList<>();

        // 提取类名（包括内部类）
        // 匹配: public/abstract/final class 类名
        java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
                "(?:public\\s+|private\\s+|protected\\s+)?(?:abstract\\s+|final\\s+)?class\\s+(\\w+)",
                java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String packageName = extractPackageName(content);
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            entities.add(Entity.builder()
                    .name(fullyQualifiedName)
                    .kind(EntityKind.CLASS)
                    .build());
        }

        // 提取方法签名（不关心方法体）
        // 匹配: public/private/protected 返回类型 方法名(参数)
        java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile(
                "(?:public\\s+|private\\s+|protected\\s+)?(?:static\\s+)?(?:\\w+\\s+)+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+\\w+)?\\s*\\{",
                java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher methodMatcher = methodPattern.matcher(content);
        String packageName = extractPackageName(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            // 尝试从上下文中找到最近的外层类（简化处理）
            String parentClass = findParentClass(content, methodMatcher.start());
            String fullyQualifiedName = packageName.isEmpty()
                    ? parentClass + "#" + methodName
                    : packageName + "." + parentClass + "#" + methodName;
            entities.add(Entity.builder()
                    .name(fullyQualifiedName)
                    .kind(EntityKind.METHOD)
                    .build());
        }

        return entities;
    }

    /**
     * 提取包名（正则辅助）
     */
    private String extractPackageName(String content) {
        java.util.regex.Pattern pkgPattern = java.util.regex.Pattern.compile(
                "^\\s*package\\s+([\\w.]+)\\s*;",
                java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher matcher = pkgPattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 查找方法所属的外层类（简化实现）
     */
    private String findParentClass(@NonNull String content, int methodPos) {
        // 简单实现：从方法位置往前找最近的 class 关键字
        String before = content.substring(0, methodPos);
        java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
                "class\\s+(\\w+)\\s*\\{?\\s*$",
                java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher matcher = classPattern.matcher(before);
        String lastClass = "Unknown";
        while (matcher.find()) {
            lastClass = matcher.group(1);
        }
        return lastClass;
    }

    /**
     * Level 1: 从 AST 提取实体（完整且精确）
     */
    private @NonNull List<Entity> extractEntities(@NonNull CompilationUnit cu) {
        List<Entity> entities = new ArrayList<>();
        String packageName = cu.getPackageDeclaration()
                .map(NodeWithName::getNameAsString)
                .orElse("");

        // 提取所有类/接口/枚举
        cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(cls -> cls.findAncestor(ClassOrInterfaceDeclaration.class).isEmpty())  // 只取顶级类
                .forEach(cls -> {
            String className = cls.getNameAsString();
            String fullyQualifiedName = packageName.isEmpty()
                    ? className
                    : packageName + "." + className;

            entities.add(Entity.builder()
                    .name(fullyQualifiedName)
                    .kind(EntityKind.CLASS)
                    .build());

            // 提取内部类（递归处理）
            cls.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .skip(1) // 跳过自身
                    .forEach(innerCls -> {
                        String innerName = innerCls.getNameAsString();
                        String innerFullyQualified = fullyQualifiedName + "$" + innerName;
                        entities.add(Entity.builder()
                                .name(innerFullyQualified)
                                .kind(EntityKind.CLASS)
                                .build());
                    });
        });

        // 提取方法（顶级方法，不进入内部类重复提取）
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            // 找到所属的父类
            Optional<ClassOrInterfaceDeclaration> parentClass = method.findAncestor(ClassOrInterfaceDeclaration.class);
            if (parentClass.isPresent()) {
                String className = parentClass.get().getNameAsString();
                String parentFullName = packageName.isEmpty()
                        ? className
                        : packageName + "." + className;

                String methodName = method.getNameAsString();
                String fullyQualifiedName = parentFullName + "#" + methodName;

                entities.add(Entity.builder()
                        .name(fullyQualifiedName)
                        .kind(EntityKind.METHOD)
                        .build());
            }
        });

        return entities;
    }

    /**
     * 构造降级结果（Level 2 或 Level 3）
     */
    private ParsingResult buildFallbackResult(DataQuality quality, String remark) {
        return ParsingResult.builder()
                .entities(Collections.emptyList())
                .quality(quality)
                .qualityRemark(remark)
                .parseDurationMs(0)
                .build();
    }
}
