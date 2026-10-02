package com.github.semanticgit.parser.java;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.entity.*;
import com.github.semanticgit.parser.java.api.EntityChange;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.AbstractParser;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.SourceCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
public class JavaParser extends AbstractParser {

    // ============ 变更 1 + 3: 预编译字段（AST 解析器 + Regex Pattern） ============
    // 注意：javaparser.JavaParser 实例不是线程安全的，多线程共享同一个 parse() 调用会
    // 导致内部状态错乱（Lexer / TokenStore）→ 解析失败 → fallback 到 regex → entity
    // key 格式全错。因此用 ThreadLocal 隔离，每个线程独立实例。
    private static final ThreadLocal<com.github.javaparser.JavaParser> AST_PARSER = ThreadLocal
            .withInitial(JavaParser::createAstParser);

    private static final Pattern CLASS_DECL_PATTERN = Pattern.compile(
            "(?:public\\s+|private\\s+|protected\\s+)?(?:abstract\\s+|final\\s+)?class\\s+(\\w+)",
            Pattern.MULTILINE);

    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile(
            "(?:public\\s+|private\\s+|protected\\s+)?(?:static\\s+)?(?:\\w+\\s+)+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+\\w+)?\\s*\\{",
            Pattern.MULTILINE);

    private static final Pattern PKG_PATTERN = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*;",
            Pattern.MULTILINE);

    private static final Pattern PARENT_CLASS_PATTERN = Pattern.compile(
            "class\\s+(\\w+)\\s*\\{?\\s*$",
            Pattern.MULTILINE);

    // ============ PERF 信号检测 Pattern ============
    private static final Pattern PERF_CONCURRENT_HM = Pattern.compile("\\bConcurrentHashMap\\b|\\bConcurrentMap\\b|\\bCopyOnWriteArrayList\\b");
    private static final Pattern PERF_COMPUTE_IF_ABSENT = Pattern.compile("\\.computeIfAbsent\\s*\\(|\\.putIfAbsent\\s*\\(|\\.computeIfPresent\\s*\\(");
    private static final Pattern PERF_PARALLEL_STREAM = Pattern.compile("\\.parallelStream\\s*\\(\\)");
    private static final Pattern PERF_PREALLOCATED_COLL = Pattern.compile("new\\s+(?:HashMap|ArrayList|HashSet|ArrayDeque|LinkedHashMap|TreeMap)\\s*\\(\\s*\\d+\\s*\\)");
    private static final Pattern PERF_PRIMITIVE = Pattern.compile("\\bInteger\\s+(\\w+);\\s*(?://[^\\n]*\\n)?\\s*(?:\\1\\s*=\\s*)?Integer\\.intValue\\s*\\(\\1\\)|\\bLong\\s+(\\w+);\\s*(?://[^\\n]*\\n)?\\s*(?:\\2\\s*=\\s*)?Long\\.longValue\\s*\\(\\2\\)");
    private static final Pattern PERF_STRING_BUILDER = Pattern.compile("new\\s+StringBuilder\\s*\\(|\\.append\\s*\\(");

    // ============ FIX 信号检测 Pattern（防御性编程新增） ============
    private static final Pattern FIX_NULL_CHECK = Pattern.compile("if\\s*\\(.*==\\s*null.*\\)", Pattern.MULTILINE);
    private static final Pattern FIX_OBJECTS_NON_NULL = Pattern.compile("Objects\\.requireNonNull\\s*\\(|Optional\\.ofNullable\\s*\\(");
    private static final Pattern FIX_BOUNDS_CHECK = Pattern.compile(
            "if\\s*\\(.*(?:index|i|j|idx|pos|offset|size|len|length).*(?:<\\s*0|>=\\s*\\w+\\.size\\s*\\(|>=\\s*\\w+\\.length|>=\\s*\\w+\\.length\\s*\\()",
            Pattern.MULTILINE);
    private static final Pattern FIX_TRY_CATCH = Pattern.compile("\\btry\\s*[({]");
    private static final Pattern FIX_THROW = Pattern.compile("\\bthrow\\s+(?:new\\s+)?(?:RuntimeException|IllegalArgumentException|IllegalStateException|NullPointerException|IndexOutOfBoundsException|Exception|Error)");
    private static final Pattern FIX_EARLY_RETURN = Pattern.compile("if\\s*\\(.*\\)\\s*\\{?\\s*return\\b", Pattern.MULTILINE);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public JavaParser(Map<String, ConfigItem<?>> configMap) {
        super(configMap);
    }

    @Override
    public void registerConfigs(Map<String, ConfigItem<?>> configMap) {
        LanguageParser.registerCommonConfigs(configMap);
    }

    @Override
    public ParsingResult parseEntities(@NonNull SourceCode sourceCode) {
        Future<ParsingResult> future = executor.submit(() -> {
            try {
                return doParse(sourceCode);
            } catch (Exception e) {
                log.error("Parse error for {}: {}", sourceCode.getFilePath(), e.getMessage());
                return buildFallbackResult(DataQuality.FILE, "CRASHED: " + e.getClass().getSimpleName());
            }
        });

        try {
            return future.get(getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Parse timeout for {}: {}ms", sourceCode.getFilePath(), getTimeoutMs());
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

    private ParsingResult doParse(@NonNull SourceCode sourceCode) {
        long startTime = System.currentTimeMillis();
        String content = sourceCode.getContent();
        String remark = "NO_ENTITIES_FOUND";

        if (content == null || content.trim().isEmpty()) {
            return buildFallbackResult(DataQuality.FILE, "EMPTY_FILE");
        }

        long sizeInBytes = sourceCode.getSizeInBytes();
        ParseResult<CompilationUnit> parseResult = null;
        if (sizeInBytes > getMaxParseSizeBytes()) {
            log.warn("File too large for AST parsing ({} bytes): {}, falling back to regex",
                    sizeInBytes, sourceCode.getFilePath());
            remark = "AST_TOO_LARGE";
        } else {
            parseResult = parseWithAST(content);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                List<Entity> entities = extractEntities(cu);

                if (!entities.isEmpty()) {
                    return ParsingResult.builder()
                            .entities(entities)
                            .quality(DataQuality.AST)
                            .qualityRemark("AST_SUCCESS")
                            .parseDurationMs(System.currentTimeMillis() - startTime)
                            .build();
                } else {
                    return buildFallbackResult(DataQuality.REGEX, "AST_NO_ENTITIES");
                }
            }
        }

        if (parseResult != null) {
            remark = "REGEX_FALLBACK" + (parseResult.getProblems().isEmpty() ? "" : ": " + parseResult.getProblems().getFirst().getMessage());
        }

        if (sizeInBytes > getMaxRegexSizeBytes()) {
            log.warn("File too large for regex parsing ({} bytes): {}, falling back to file",
                    sizeInBytes, sourceCode.getFilePath());
            remark = "REGEX_TOO_LARGE";
        } else {
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

        return buildFallbackResult(DataQuality.FILE, remark);
    }

    private static com.github.javaparser.JavaParser createAstParser() {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        config.setStoreTokens(true);
        return new com.github.javaparser.JavaParser(config);
    }

    protected ParseResult<CompilationUnit> parseWithAST(String content) {
        return AST_PARSER.get().parse(ParseStart.COMPILATION_UNIT, new StringProvider(content));
    }

    private @NonNull List<Entity> extractWithRegex(String content) {
        List<Entity> entities = new ArrayList<>();

        java.util.regex.Matcher classMatcher = CLASS_DECL_PATTERN.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String packageName = extractPackageName(content);
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
            entities.add(Entity.builder()
                    .name(fullyQualifiedName)
                    .kind(EntityKind.CLASS)
                    .build());
        }

        java.util.regex.Matcher methodMatcher = METHOD_DECL_PATTERN.matcher(content);
        String packageName = extractPackageName(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            String params = methodMatcher.group(2).replaceAll("\\s+", "");
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

    private String extractPackageName(String content) {
        java.util.regex.Matcher matcher = PKG_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String findParentClass(@NonNull String content, int methodPos) {
        String before = content.substring(0, methodPos);
        java.util.regex.Matcher matcher = PARENT_CLASS_PATTERN.matcher(before);
        String lastClass = "Unknown";
        while (matcher.find()) {
            lastClass = matcher.group(1);
        }
        return lastClass;
    }

    private @NonNull List<Entity> extractEntities(@NonNull CompilationUnit cu) {
        List<Entity> entities = new ArrayList<>();
        String packageName = cu.getPackageDeclaration()
                .map(NodeWithName::getNameAsString)
                .orElse("");

        cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(cls -> cls.findAncestor(ClassOrInterfaceDeclaration.class).isEmpty())
                .forEach(cls -> {
            String className = cls.getNameAsString();
            String fullyQualifiedName = packageName.isEmpty()
                    ? className
                    : packageName + "." + className;

            String classSig = buildClassSignature(cls);
            entities.add(Entity.builder()
                    .name(fullyQualifiedName)
                    .kind(EntityKind.CLASS)
                    .signature(classSig)
                    .build());

            cls.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .skip(1)
                    .forEach(innerCls -> {
                        String innerName = innerCls.getNameAsString();
                        String innerFullyQualified = fullyQualifiedName + "$" + innerName;
                        String innerSig = buildClassSignature(innerCls);
                        entities.add(Entity.builder()
                                .name(innerFullyQualified)
                                .kind(EntityKind.CLASS)
                                .signature(innerSig)
                                .build());
                    });
        });

        cu.findAll(MethodDeclaration.class).forEach(method -> {
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

                String methodSig = buildMethodSignature(method);
                entities.add(Entity.builder()
                        .name(fullyQualifiedName)
                        .kind(EntityKind.METHOD)
                        .signature(methodSig)
                        .build());
            }
        });

        return entities;
    }

    private @NonNull String buildClassSignature(@NonNull ClassOrInterfaceDeclaration cls) {
        String superName = cls.getExtendedTypes().stream()
                .map(Node::toString)
                .findFirst()
                .orElse("");
        String interfaces = cls.getImplementedTypes().stream()
                .map(Node::toString)
                .sorted()
                .collect(Collectors.joining(","));
        int fieldCount = (int) cls.getFields().size();
        String methodSigs = cls.getMethods().stream()
                .map(m -> m.getNameAsString() + "(" + m.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .collect(Collectors.joining(",")) + ")")
                .sorted()
                .collect(Collectors.joining(";"));
        return superName + ":" + interfaces + ":" + fieldCount + ":" + methodSigs;
    }

    private @NonNull String buildMethodSignature(@NonNull MethodDeclaration method) {
        String returnType = method.getType().asString();
        String params = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        String bodyHash = method.getBody()
                .map(b -> Integer.toHexString(b.toString().hashCode()))
                .orElse("0");
        return returnType + ":" + params + ":" + bodyHash;
    }

    private ParsingResult buildFallbackResult(DataQuality quality, String remark) {
        return ParsingResult.builder()
                .entities(Collections.emptyList())
                .quality(quality)
                .qualityRemark(remark)
                .parseDurationMs(0)
                .build();
    }

    /**
     * 直接解析 sourceCode 为 entity map，绕过 executor（用于 parseChangeNatureFlags 内部并行，
     * 避免与外层 executor.submit 形成嵌套提交链导致死锁）。
     */
    private Map<String, Entity> parseEntityMapDirect(SourceCode sourceCode) {
        if (sourceCode == null || sourceCode.getContent() == null) {
            return Collections.emptyMap();
        }
        try {
            ParsingResult result = doParse(sourceCode);
            return result.getEntities().stream()
                    .collect(Collectors.toMap(Entity::getName, e -> e, (a, _) -> a));
        } catch (Exception e) {
            log.error("Direct parse error for {}", sourceCode.getFilePath(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public List<EntityChange> parseChangeNatureFlags(SourceCode sourceCodeBefore, SourceCode sourceCodeAfter) {
        List<EntityChange> changes = new ArrayList<>();

        // ============ 变更 2: before / after 并行解析 ============
        CompletableFuture<Map<String, Entity>> beforeFuture = CompletableFuture.supplyAsync(() ->
                sourceCodeBefore != null ? parseEntityMapDirect(sourceCodeBefore) : Collections.emptyMap());
        CompletableFuture<Map<String, Entity>> afterFuture = CompletableFuture.supplyAsync(() ->
                sourceCodeAfter != null ? parseEntityMapDirect(sourceCodeAfter) : Collections.emptyMap());

        Map<String, Entity> beforeEntities;
        Map<String, Entity> afterEntities;
        try {
            beforeEntities = beforeFuture.get(getTimeoutMs(), TimeUnit.MILLISECONDS);
            afterEntities = afterFuture.get(getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            beforeFuture.cancel(true);
            afterFuture.cancel(true);
            log.warn("Parallel parse timeout for change nature flags: {}ms", getTimeoutMs());
            beforeEntities = Collections.emptyMap();
            afterEntities = Collections.emptyMap();
        } catch (Exception e) {
            log.error("Parallel parse error for change nature flags: {}", e.getMessage());
            beforeEntities = Collections.emptyMap();
            afterEntities = Collections.emptyMap();
        }

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

        Set<String> removedNames = new HashSet<>(beforeEntities.keySet());
        removedNames.removeAll(afterEntities.keySet());

        // ============ P0: 同文件 REFACTOR 检测 ============
        // 对剩余 removed ↔ added 做 signature 相似度配对，匹配成功识别为重命名/提取/移动
        Set<String> matchedAdded = new HashSet<>();
        Set<String> matchedRemoved = new HashSet<>();

        for (String removedName : removedNames) {
            Entity removedEntity = beforeEntities.get(removedName);
            for (String addedName : addedNames) {
                if (matchedAdded.contains(addedName)) continue;

                Entity addedEntity = afterEntities.get(addedName);
                if (removedEntity.getKind() != addedEntity.getKind()) continue;

                double similarity = computeStructuralSimilarityLocal(removedEntity, addedEntity);
                if (similarity >= 0.7) {
                    String beforeBody = extractEntityBody(sourceCodeBefore, removedName);
                    String afterBody = extractEntityBody(sourceCodeAfter, addedName);
                    int flags = (parseEntityChangeNatureFlag(beforeBody, afterBody)
                            | ChangeNatureFlag.REFACTOR.code | testFlag);
                    changes.add(EntityChange.builder()
                            .before(removedEntity)
                            .after(addedEntity)
                            .flags(flags)
                            .build());
                    matchedAdded.add(addedName);
                    matchedRemoved.add(removedName);
                    break;
                }
            }
        }

        // 未配对的 removed / added 仍按 FEAT 处理
        for (String name : removedNames) {
            if (matchedRemoved.contains(name)) continue;
            String beforeBody = extractEntityBody(sourceCodeBefore, name);
            int flags = parseEntityChangeNatureFlag(beforeBody, null) | testFlag;
            changes.add(EntityChange.builder()
                    .before(beforeEntities.get(name))
                    .after(null)
                    .flags(flags)
                    .build());
        }
        for (String name : addedNames) {
            if (matchedAdded.contains(name)) continue;
            String afterBody = extractEntityBody(sourceCodeAfter, name);
            int flags = parseEntityChangeNatureFlag(null, afterBody) | testFlag;
            changes.add(EntityChange.builder()
                    .before(null)
                    .after(afterEntities.get(name))
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

        // 空新增 / 空删除 → FEAT（PERF / FIX 信号只在 MODIFY 场景检测）
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

        // ======== 实质性变更：叠加 PERF / FIX 信号 ========
        int flags = 0;

        // FIX 信号 1：原有长度缩减规则
        if (sourceCodeAfter.length() < sourceCodeBefore.length() * 0.8) {
            flags |= ChangeNatureFlag.FIX.code;
        }
        // FIX 信号 2：新增防御性编程模式
        if (countFixSignals(sourceCodeBefore, sourceCodeAfter) > 0) {
            flags |= ChangeNatureFlag.FIX.code;
        }

        // PERF 信号：after 中出现性能优化模式且 before 中没有
        if (countPerfSignals(sourceCodeBefore, sourceCodeAfter) > 0) {
            flags |= ChangeNatureFlag.PERF.code;
        }

        // 兜底：未命中任何特定信号 → FEAT
        if (flags == 0) {
            flags = ChangeNatureFlag.FEAT.code;
        }
        return flags;
    }

    /**
     * 检测 after 中相比 before 新增的性能优化信号数量。
     * 每个 Pattern 命中 1 次就算 1 个信号，不同 Pattern 独立计数后求和。
     */
    private int countPerfSignals(@NonNull String before, @NonNull String after) {
        int afterHits = 0;
        int beforeHits = 0;

        afterHits += countMatches(PERF_CONCURRENT_HM, after);
        beforeHits += countMatches(PERF_CONCURRENT_HM, before);

        afterHits += countMatches(PERF_COMPUTE_IF_ABSENT, after);
        beforeHits += countMatches(PERF_COMPUTE_IF_ABSENT, before);

        afterHits += countMatches(PERF_PARALLEL_STREAM, after);
        beforeHits += countMatches(PERF_PARALLEL_STREAM, before);

        afterHits += countMatches(PERF_PREALLOCATED_COLL, after);
        beforeHits += countMatches(PERF_PREALLOCATED_COLL, before);

        afterHits += countMatches(PERF_PRIMITIVE, after);
        beforeHits += countMatches(PERF_PRIMITIVE, before);

        afterHits += countMatches(PERF_STRING_BUILDER, after);
        beforeHits += countMatches(PERF_STRING_BUILDER, before);

        return Math.max(0, afterHits - beforeHits);
    }

    /**
     * 检测 after 中相比 before 新增的防御性编程信号数量。
     */
    private int countFixSignals(@NonNull String before, @NonNull String after) {
        int afterHits = 0;
        int beforeHits = 0;

        afterHits += countMatches(FIX_NULL_CHECK, after);
        beforeHits += countMatches(FIX_NULL_CHECK, before);

        afterHits += countMatches(FIX_OBJECTS_NON_NULL, after);
        beforeHits += countMatches(FIX_OBJECTS_NON_NULL, before);

        afterHits += countMatches(FIX_BOUNDS_CHECK, after);
        beforeHits += countMatches(FIX_BOUNDS_CHECK, before);

        afterHits += countMatches(FIX_TRY_CATCH, after);
        beforeHits += countMatches(FIX_TRY_CATCH, before);

        afterHits += countMatches(FIX_THROW, after);
        beforeHits += countMatches(FIX_THROW, before);

        afterHits += countMatches(FIX_EARLY_RETURN, after);
        beforeHits += countMatches(FIX_EARLY_RETURN, before);

        return Math.max(0, afterHits - beforeHits);
    }

    private int countMatches(@NonNull Pattern pattern, @NonNull String input) {
        int count = 0;
        java.util.regex.Matcher m = pattern.matcher(input);
        while (m.find()) {
            count++;
        }
        return count;
    }

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
                .collect(Collectors.toMap(Entity::getName, e -> e, (a, _) -> a));
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

    private String extractEntityBody(SourceCode sourceCode, String entityName) {
        if (sourceCode == null || sourceCode.getContent() == null || entityName == null) {
            return null;
        }
        String content = sourceCode.getContent();

        try {
            if (sourceCode.getSizeInBytes() <= getMaxParseSizeBytes()) {
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

    // ============ 同文件 REFACTOR 相似度算法（从 core.AnalysisEngine 下沉一份独立实现） ============

    private double computeStructuralSimilarityLocal(@NonNull Entity removed, @NonNull Entity added) {
        String sig1 = removed.getSignature();
        String sig2 = added.getSignature();

        if (sig1 == null || sig2 == null || sig1.isEmpty() || sig2.isEmpty()) {
            return 0.0;
        }

        if (removed.getKind() == EntityKind.METHOD) {
            return computeMethodSimilarityLocal(sig1, sig2);
        }
        if (removed.getKind() == EntityKind.CLASS) {
            return computeClassSimilarityLocal(sig1, sig2);
        }
        return 0.0;
    }

    private double computeMethodSimilarityLocal(@NonNull String sig1, @NonNull String sig2) {
        String[] parts1 = sig1.split(":", 3);
        String[] parts2 = sig2.split(":", 3);

        if (parts1.length < 2 || parts2.length < 2) {
            return 0.0;
        }

        // Method signature: returnType:params:bodyHash
        // 重命名/参数调整后 returnType 和 params 应不变
        if (!parts1[0].equals(parts2[0]) || !parts1[1].equals(parts2[1])) {
            return 0.0;
        }

        // bodyHash 完全一致 → 1.0（纯重命名）
        if (parts1.length == 3 && parts2.length == 3 && parts1[2].equals(parts2[2])) {
            return 1.0;
        }

        // bodyHash 不同（方法提取/内联导致 hash 变了） → 0.7 阈值内刚好够
        return 0.7;
    }

    private double computeClassSimilarityLocal(@NonNull String sig1, @NonNull String sig2) {
        String[] parts1 = sig1.split(":", 4);
        String[] parts2 = sig2.split(":", 4);

        if (parts1.length < 4 || parts2.length < 4) {
            return 0.0;
        }

        // Class signature: superName:interfaces:fieldCount:methodSigs
        // 重命名不应改变继承 / 接口 / 字段数
        if (!parts1[0].equals(parts2[0]) || !parts1[1].equals(parts2[1])) {
            return 0.0;
        }

        double score;
        if (!parts1[2].equals(parts2[2])) {
            score = 0.3; // 字段数变了，但继承/接口相同
        } else {
            score = 0.6; // 字段数也没变
        }

        // 方法集合 Jaccard 相似度提升分
        Set<String> methods1 = new HashSet<>(Arrays.asList(parts1[3].split(";")));
        Set<String> methods2 = new HashSet<>(Arrays.asList(parts2[3].split(";")));
        methods1.remove("");
        methods2.remove("");

        Set<String> intersection = new HashSet<>(methods1);
        intersection.retainAll(methods2);
        Set<String> union = new HashSet<>(methods1);
        union.addAll(methods2);

        if (!union.isEmpty()) {
            double jaccard = (double) intersection.size() / union.size();
            score = Math.min(1.0, score + jaccard * 0.4);
        }

        return score;
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
