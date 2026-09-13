package com.github.semanticgit.parser.java;

import com.github.javaparser.ast.Node;
import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.parser.java.api.AbstractParser;
import com.github.semanticgit.parser.java.api.EntityChange;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.SourceCode;
import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
public class JavaParser extends AbstractParser<JavaParserConfig> {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final ParsingResult OOM_FALLBACK = ParsingResult.builder()
            .entities(Collections.emptyList())
            .quality(DataQuality.FILE)
            .qualityRemark("OUT_OF_MEMORY")
            .parseDurationMs(0)
            .build();

    public JavaParser(JavaParserConfig config) {
        super(config);
    }

    @Override
    public ParsingResult parseEntities(@NonNull SourceCode sourceCode) {
        // 使用超时机制，防止解析卡死
        Future<ParsingResult> future = executor.submit(() -> {
            try {
                return doParse(sourceCode);
            } catch (Exception e) {
                log.error("Parse error for {}: {}", sourceCode.getFilePath(), e.getMessage());
                return buildFallbackResult(DataQuality.FILE, "CRASHED: " + e.getClass().getSimpleName());
            } catch (OutOfMemoryError e) {
                System.gc();

                // 短暂让出 CPU，给 GC 时间执行
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                log.error("Out of memory when parsing {}: {}", sourceCode.getFilePath(), e.getMessage());
                return OOM_FALLBACK;
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
    private ParsingResult doParse(@NonNull SourceCode sourceCode) {
        long startTime = System.currentTimeMillis();
        String content = sourceCode.getContent();
        String remark = "NO_ENTITIES_FOUND";

        // 空文件直接返回文件级结果
        if (content == null || content.trim().isEmpty()) {
            return buildFallbackResult(DataQuality.FILE, "EMPTY_FILE");
        }

        long sizeInBytes = sourceCode.getSizeInBytes();
        ParseResult<CompilationUnit> parseResult = null;
        if (sizeInBytes > config.getMaxParseSizeBytes()) {
            log.warn("File too large for AST parsing ({} bytes): {}, falling back to regex",
                    sizeInBytes, sourceCode.getFilePath());
            remark = "AST_TOO_LARGE";
        } else {
            // Level 1: 尝试 AST 完美解析
            parseResult = parseWithAST(content);

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
                    return buildFallbackResult(DataQuality.REGEX, "AST_NO_ENTITIES");
                }
            }
        }

        if (parseResult != null) {
            remark = "REGEX_FALLBACK" + (parseResult.getProblems().isEmpty() ? "" : ": " + parseResult.getProblems().getFirst().getMessage());
        }

        if (sizeInBytes > config.getMaxRegexSizeBytes()) {
            log.warn("File too large for regex parsing ({} bytes): {}, falling back to file",
                    sizeInBytes, sourceCode.getFilePath());
            remark = "REGEX_TOO_LARGE";
        } else {
            // Level 2: 正则回退（AST 部分失败）
            List<Entity> regexEntities = extractWithRegex(content);
            if (!regexEntities.isEmpty()) {
                return ParsingResult.builder()
                        .entities(regexEntities)
                        .quality(DataQuality.REGEX)
                        .qualityRemark(remark)
                        .parseDurationMs(System.currentTimeMillis() - startTime)
                        .build();
            }
        }

        // Level 3: 文件级兜底
        return buildFallbackResult(DataQuality.FILE, remark);
    }

    /**
     * 使用 {@link com.github.javaparser.JavaParser} 进行 AST 解析
     */
    protected ParseResult<CompilationUnit> parseWithAST(String content) {
        ParserConfiguration parserConfig = new ParserConfiguration();
        parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17); // 可配置化
        parserConfig.setStoreTokens(true); // 便于调试

        com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser(parserConfig);
        return parser.parse(ParseStart.COMPILATION_UNIT, new StringProvider(content));
    }

    /**
     * 正则表达式提取（降级方案）
     * 仅提取类名和方法签名，不关心方法体是否正确
     */
    private @NonNull List<Entity> extractWithRegex(String content) {
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
                "(?:public\\s+|private\\s+|protected\\s+)?(?:static\\s+)?(?:\\w+\\s+)+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+\\w+)?\\s*\\{",
                java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher methodMatcher = methodPattern.matcher(content);
        String packageName = extractPackageName(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            String params = methodMatcher.group(2).replaceAll("\\s+", ""); // 去空白
            String parentClass = findParentClass(content, methodMatcher.start());
            String fullyQualifiedName = packageName.isEmpty()
                    ? parentClass + "#" + methodName + "(" + params + ")"
                    : packageName + "." + parentClass + "#" + methodName + "(" + params + ")";
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
     * 从 AST 提取实体
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

                String params = method.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .collect(Collectors.joining(","));
                String fullyQualifiedName = parentFullName + "#" + method.getNameAsString() + "(" + params + ")";

                entities.add(Entity.builder()
                        .name(fullyQualifiedName)
                        .kind(EntityKind.METHOD)
                        .build());
            }
        });

        return entities;
    }

    /**
     * 构造降级结果
     */
    private ParsingResult buildFallbackResult(DataQuality quality, String remark) {
        return ParsingResult.builder()
                .entities(Collections.emptyList())
                .quality(quality)
                .qualityRemark(remark)
                .parseDurationMs(0)
                .build();
    }

    @Override
    public List<EntityChange> parseChangeNatureFlags(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter) {
        List<EntityChange> changes = new ArrayList<>();

        Map<String, Entity> beforeEntities = sourceCodeBefore != null
                ? parseEntityMap(sourceCodeBefore) : Collections.emptyMap();
        Map<String, Entity> afterEntities = sourceCodeAfter != null
                ? parseEntityMap(sourceCodeAfter) : Collections.emptyMap();

        SourceCode fileHint = sourceCodeAfter != null ? sourceCodeAfter : sourceCodeBefore;
        int testFlag = isTestFile(fileHint) ? ChangeNatureFlag.TEST.code : 0;

        Set<String> commonNames = new HashSet<>(beforeEntities.keySet());
        commonNames.retainAll(afterEntities.keySet());
        for (String name : commonNames) {
            String beforeBody = extractEntityBody(sourceCodeBefore, name);
            String afterBody = extractEntityBody(sourceCodeAfter, name);
            int flags = parseEntityChangeNatureFlag(beforeBody, afterBody) | testFlag;
            changes.add(EntityChange.builder()
                    .before(beforeEntities.get(name))
                    .after(afterEntities.get(name))
                    .flags(flags)
                    .build());
        }

        Set<String> addedNames = new HashSet<>(afterEntities.keySet());
        addedNames.removeAll(beforeEntities.keySet());
        for (String name : addedNames) {
            String afterBody = extractEntityBody(sourceCodeAfter, name);
            int flags = parseEntityChangeNatureFlag(null, afterBody) | testFlag;
            changes.add(EntityChange.builder()
                    .before(null)
                    .after(afterEntities.get(name))
                    .flags(flags)
                    .build());
        }

        Set<String> removedNames = new HashSet<>(beforeEntities.keySet());
        removedNames.removeAll(afterEntities.keySet());
        for (String name : removedNames) {
            String beforeBody = extractEntityBody(sourceCodeBefore, name);
            int flags = parseEntityChangeNatureFlag(beforeBody, null) | testFlag;
            changes.add(EntityChange.builder()
                    .before(beforeEntities.get(name))
                    .after(null)
                    .flags(flags)
                    .build());
        }

        return changes;
    }

    @Override
    public int parseEntityChangeNatureFlag(String sourceCodeBefore, String sourceCodeAfter) {
        boolean beforeEmpty = sourceCodeBefore == null || sourceCodeBefore.trim().isEmpty();
        boolean afterEmpty = sourceCodeAfter == null || sourceCodeAfter.trim().isEmpty();

        if (beforeEmpty && afterEmpty) {
            return 0;
        }

        if (beforeEmpty) {
            return ChangeNatureFlag.FEAT.code;
        }

        if (afterEmpty) {
            return ChangeNatureFlag.FEAT.code;
        }

        if (sourceCodeBefore.equals(sourceCodeAfter)) {
            return 0;
        }

        String beforeNormalized = normalizeForComparison(sourceCodeBefore);
        String afterNormalized = normalizeForComparison(sourceCodeAfter);

        if (beforeNormalized.equals(afterNormalized)) {
            if (isDocsChangeStr(sourceCodeBefore, sourceCodeAfter)) {
                return ChangeNatureFlag.DOCS.code;
            }
            if (isStyleChangeStr(sourceCodeBefore, sourceCodeAfter)) {
                return ChangeNatureFlag.STYLE.code;
            }
            return 0;
        }

        if (sourceCodeAfter.length() < sourceCodeBefore.length() * 0.8) {
            return ChangeNatureFlag.FIX.code;
        }
        return ChangeNatureFlag.FEAT.code;
    }

    /**
     * 规范化代码用于比较：去除空白和注释
     */
    private @NonNull String normalizeForComparison(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("//.*", "")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("\\s+", "");
    }

    private Map<String, Entity> parseEntityMap(SourceCode sourceCode) {
        if (sourceCode == null || sourceCode.getContent() == null) {
            return Collections.emptyMap();
        }
        ParsingResult result = parseEntities(sourceCode);
        return result.getEntities().stream()
                .collect(java.util.stream.Collectors.toMap(Entity::getName, e -> e, (a, _) -> a));
    }

    private boolean isDocsChangeStr(String before, String after) {
        if (before == null || after == null) {
            return false;
        }
        String beforeCode = before
                .replaceAll("//[^\n]*\n?", "\n")
                .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "")
                .replaceAll("\\s+", " ")
                .trim();
        String afterCode = after
                .replaceAll("//[^\n]*\n?", "\n")
                .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "")
                .replaceAll("\\s+", " ")
                .trim();
        return beforeCode.equals(afterCode);
    }

    private boolean isStyleChangeStr(String before, String after) {
        if (before == null || after == null) {
            return false;
        }
        String beforeNormalized = before.replaceAll("\\s+", "");
        String afterNormalized = after.replaceAll("\\s+", "");
        return beforeNormalized.equals(afterNormalized);
    }

    /**
     * 判断是否为测试文件
     */
    private boolean isTestFile(SourceCode sourceCode) {
        if (sourceCode == null || sourceCode.getFilePath() == null) {
            return false;
        }
        String filePath = sourceCode.getFilePath();
        return filePath.contains("/test/")
                || filePath.contains("\\test\\")
                || filePath.endsWith("Test.java")
                || filePath.endsWith("Tests.java");
    }

    /**
     * 从源码中提取指定实体的方法体/类体
     */
    private String extractEntityBody(SourceCode sourceCode, String entityName) {
        if (sourceCode == null || sourceCode.getContent() == null || entityName == null) {
            return null;
        }
        String content = sourceCode.getContent();

        try {
            if (sourceCode.getSizeInBytes() <= config.getMaxParseSizeBytes()) {
                ParseResult<CompilationUnit> parseResult = parseWithAST(content);
                if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                    CompilationUnit cu = parseResult.getResult().get();
                    String packageName = cu.getPackageDeclaration()
                            .map(NodeWithName::getNameAsString)
                            .orElse("");

                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        String className = cls.getNameAsString();
                        String parentFullName = packageName.isEmpty() ? className : packageName + "." + className;

                        if (parentFullName.equals(entityName)) {
                            return extractRange(content, cls);
                        }

                        for (MethodDeclaration method : cls.getMethods()) {
                            String params = method.getParameters().stream()
                                    .map(p -> p.getType().asString())
                                    .collect(Collectors.joining(","));
                            String methodFullName = parentFullName + "#" + method.getNameAsString() + "(" + params + ")";

                            if (methodFullName.equals(entityName)) {
                                return extractRange(content, method);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AST extraction failed for entity {}: {}", entityName, e.getMessage());
        }
        return null;
    }

    private String extractRange(String content, @NonNull Node node) {
        return node.getRange()
                .map(r -> {
                    int start = posToOffset(content, r.begin.line, r.begin.column);
                    int end = posToOffset(content, r.end.line, r.end.column);
                    return content.substring(start, end);
                })
                .orElse(node.toString());
    }

    private int posToOffset(@NonNull String content, int line, int column) {
        int currentLine = 1;
        int currentCol = 1;
        for (int i = 0; i < content.length(); i++) {
            if (currentLine == line && currentCol == column) {
                return i;
            }
            if (content.charAt(i) == '\n') {
                currentLine++;
                currentCol = 1;
            } else {
                currentCol++;
            }
        }
        return content.length();
    }
}
