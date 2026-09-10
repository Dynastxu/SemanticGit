package com.github.semanticgit.parser.java;

import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.parser.java.api.AbstractParser;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

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
    public int parseChangeNatureFlag(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter, String entityName) {
        int flags = 0;

        // 1. 判断是否为测试文件（取前后中非 null 的一方）
        SourceCode fileHint = sourceCodeAfter != null ? sourceCodeAfter : sourceCodeBefore;
        if (isTestFile(fileHint)) {
            flags |= ChangeNatureFlag.TEST.code;
        }

        // 2. 判断是否仅为样式变更（仅空白字符差异）
        if (isStyleOnlyChange(sourceCodeBefore, sourceCodeAfter)) {
            flags |= ChangeNatureFlag.STYLE.code;
        }

        // 3. 判断是否仅为文档变更（仅注释差异）
        if (isDocsOnlyChange(sourceCodeBefore, sourceCodeAfter)) {
            flags |= ChangeNatureFlag.DOCS.code;
        }

        // 4. 实体级别分析：根据 entityName 定位具体类/方法，判断变更性质
        if (entityName != null && !entityName.isEmpty()) {
            int entityFlags = analyzeEntityChange(sourceCodeBefore, sourceCodeAfter, entityName);
            flags |= entityFlags;
        }

        // 如果没有任何标志匹配，默认返回 FEAT
        if (flags == 0) {
            return ChangeNatureFlag.FEAT.code;
        }

        return flags;
    }

    /**
     * 分析指定实体的变更性质，返回实体级别的 flag 位掩码
     */
    private int analyzeEntityChange(SourceCode before, SourceCode after, String entityName) {
        int flags = 0;

        boolean entityExistedBefore = entityExistsInSource(before, entityName);
        boolean entityExistsAfter = entityExistsInSource(after, entityName);

        if (!entityExistedBefore && entityExistsAfter) {
            // 新增实体 → FEAT
            flags |= ChangeNatureFlag.FEAT.code;
        } else if (entityExistedBefore && !entityExistsAfter) {
            // 移除实体 → FEAT（也可能是重构的前半部分）
            flags |= ChangeNatureFlag.FEAT.code;
        } else if (entityExistedBefore && entityExistsAfter) {
            // 实体在前后都存在，判断具体变更类型
            String beforeBody = extractEntityBody(before, entityName);
            String afterBody = extractEntityBody(after, entityName);

            if (beforeBody != null && afterBody != null) {
                // 去除空白和注释后比较
                String beforeNormalized = normalizeForComparison(beforeBody);
                String afterNormalized = normalizeForComparison(afterBody);

                if (beforeNormalized.equals(afterNormalized)) {
                    // 内容无实质变更，但实体在前后版本都存在
                    // 可能是仅格式/注释变更，已在上面处理
                } else {
                    // 实体有实质变更
                    String entitySimpleName = extractSimpleName(entityName);

                    if (entitySimpleName != null && !entitySimpleName.equals(extractSimpleName(entityName))) {
                        // 方法签名变更 → REFACTOR
                        flags |= ChangeNatureFlag.REFACTOR.code;
                    } else {
                        // 方法体变更但签名不变 → 可能是 FIX 或 FEAT
                        // 保守判断：如果方法体变小，可能是 FIX；变大，可能是 FEAT
                        if (afterBody.length() < beforeBody.length() * 0.8) {
                            flags |= ChangeNatureFlag.FIX.code;
                        } else {
                            flags |= ChangeNatureFlag.FEAT.code;
                        }
                    }
                }
            } else {
                // 无法提取实体体，保守返回 FEAT
                flags |= ChangeNatureFlag.FEAT.code;
            }
        }

        return flags;
    }

    /**
     * 判断实体是否存在于源码中
     */
    private boolean entityExistsInSource(SourceCode sourceCode, String entityName) {
        if (sourceCode == null || sourceCode.getContent() == null || entityName == null) {
            return false;
        }
        // 实体名格式：com.example.Class 或 com.example.Class#method
        String simpleName = extractSimpleName(entityName);
        return simpleName != null && sourceCode.getContent().contains(simpleName);
    }

    /**
     * 从全限定名中提取简名
     */
    private String extractSimpleName(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return null;
        }
        // 处理方法：com.example.Class#method → method
        int hashIdx = fullyQualifiedName.indexOf('#');
        if (hashIdx >= 0) {
            return fullyQualifiedName.substring(hashIdx + 1);
        }
        // 处理类：com.example.Class → Class
        int dotIdx = fullyQualifiedName.lastIndexOf('.');
        if (dotIdx >= 0) {
            return fullyQualifiedName.substring(dotIdx + 1);
        }
        return fullyQualifiedName;
    }

    /**
     * 从源码中提取指定实体的方法体/类体
     */
    private String extractEntityBody(SourceCode sourceCode, String entityName) {
        if (sourceCode == null || sourceCode.getContent() == null) {
            return null;
        }
        String content = sourceCode.getContent();
        String simpleName = extractSimpleName(entityName);
        if (simpleName == null) {
            return null;
        }

        // 尝试用 AST 解析并提取实体体
        try {
            if (sourceCode.getSizeInBytes() <= config.getMaxParseSizeBytes()) {
                ParseResult<CompilationUnit> parseResult = parseWithAST(content);
                if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                    CompilationUnit cu = parseResult.getResult().get();
                    // 查找方法
                    for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                        if (method.getNameAsString().equals(simpleName)) {
                            return method.getBody()
                                    .map(body -> body.toString())
                                    .orElse(method.toString());
                        }
                    }
                    // 查找类
                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (cls.getNameAsString().equals(simpleName)) {
                            return cls.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AST extraction failed for entity {}: {}", entityName, e.getMessage());
        }

        return null;
    }

    /**
     * 规范化代码用于比较：去除空白和注释
     */
    private String normalizeForComparison(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("//.*", "")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("\\s+", "");
    }

    /**
     * 判断是否为测试文件
     */
    private boolean isTestFile(SourceCode sourceCode) {
        if (sourceCode == null) {
            return false;
        }
        String filePath = sourceCode.getFilePath();
        return filePath != null && (
            filePath.contains("/test/") ||
            filePath.contains("\\test\\") ||
            filePath.endsWith("Test.java") ||
            filePath.endsWith("Tests.java")
        );
    }

    /**
     * 判断是否仅为样式变更（去除空白后内容相同）
     */
    private boolean isStyleOnlyChange(SourceCode before, SourceCode after) {
        if (before == null || after == null) {
            return false;
        }
        String beforeNormalized = before.getContent().replaceAll("\\s+", "");
        String afterNormalized = after.getContent().replaceAll("\\s+", "");
        return beforeNormalized.equals(afterNormalized);
    }

    /**
     * 判断是否仅为文档变更（去除注释后内容相同）
     */
    private boolean isDocsOnlyChange(SourceCode before, SourceCode after) {
        if (before == null || after == null) {
            return false;
        }
        // 移除单行和多行注释后比较
        String beforeCode = before.getContent().replaceAll("//.*", "").replaceAll("/\\*.*?\\*/", "");
        String afterCode = after.getContent().replaceAll("//.*", "").replaceAll("/\\*.*?\\*/", "");
        return beforeCode.equals(afterCode);
    }
}
