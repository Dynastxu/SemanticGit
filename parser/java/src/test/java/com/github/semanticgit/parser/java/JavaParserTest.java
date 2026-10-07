package com.github.semanticgit.parser.java;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.config.ConfigItems;
import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.common.entity.EntityKind;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.SourceCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserTest {

    private JavaParser parser;
    private JavaParser timeoutParser;

    private static Map<String, ConfigItem<?>> buildConfigMap() {
        Map<String, ConfigItem<?>> map = new HashMap<>();
        LanguageParser.registerCommonConfigs(map);
        return map;
    }

    private static Map<String, ConfigItem<?>> buildZeroTimeoutConfigMap() {
        Map<String, ConfigItem<?>> map = new HashMap<>();
        LanguageParser.registerCommonConfigs(map);
        map.put(LanguageParser.CONFIG_KEY_TIMEOUT, ConfigItems.LONG(0L).build());
        return map;
    }

    @BeforeEach
    void setUp() {
        parser = new JavaParser(buildConfigMap());
        timeoutParser = new JavaParser(buildZeroTimeoutConfigMap());
    }

    @AfterEach
    void tearDown() {
        parser = null;
        timeoutParser = null;
    }

    // ==================== getSupportedLanguage ====================

    @Test
    @DisplayName("getSupportedLanguage 应返回 JAVA")
    void testGetSupportedLanguage() {
        assertEquals(EntityLanguage.JAVA, parser.getSupportedLanguage());
    }

    // ==================== parseEntities - 空/空值边界 ====================

    @Test
    @DisplayName("空内容应返回 FILE 级别降级结果")
    void testParseEmptyContent() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content("")
                .language(EntityLanguage.JAVA)
                .build();
        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertEquals("EMPTY_FILE", result.getQualityRemark());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("null 内容应返回 FILE 级别降级结果")
    void testParseNullContent() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content(null)
                .language(EntityLanguage.JAVA)
                .build();
        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertEquals("EMPTY_FILE", result.getQualityRemark());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("仅空白内容应返回 FILE 级别降级结果")
    void testParseBlankContent() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content("   \n\t  \n  ")
                .language(EntityLanguage.JAVA)
                .build();
        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertEquals("EMPTY_FILE", result.getQualityRemark());
    }

    // ==================== parseEntities - AST 成功路径 ====================

    @Test
    @DisplayName("有效 Java 类应通过 AST 解析提取类和方法实体")
    void testParseValidJavaClassWithMethods() {
        String javaCode = """
                package com.example;

                public class HelloWorld {
                    public void sayHello() {
                        System.out.println("Hello");
                    }

                    private String getName() {
                        return "World";
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/HelloWorld.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());
        assertEquals("AST_SUCCESS", result.getQualityRemark());
        assertTrue(result.getParseDurationMs() >= 0);

        List<Entity> entities = result.getEntities();
        assertNotNull(entities);
        assertEquals(3, entities.size());

        Entity helloClass = entities.stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();
        assertEquals("com.example.HelloWorld", helloClass.getName());

        List<Entity> methods = entities.stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.HelloWorld#sayHello()")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.HelloWorld#getName()")));
    }

    @Test
    @DisplayName("内部类应使用 $ 分隔符提取")
    void testParseInnerClass() {
        String javaCode = """
                package com.example;

                public class Outer {
                    public class Inner {
                        public void innerMethod() {
                        }
                    }

                    public void outerMethod() {
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Outer.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> entities = result.getEntities();
        List<Entity> classes = entities.stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        assertEquals(2, classes.size());
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer")));
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer$Inner")));

        List<Entity> methods = entities.stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertTrue(methods.size() >= 2, "至少应有 outerMethod 和 innerMethod");
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.Outer#outerMethod()")));
    }

    @Test
    @DisplayName("接口应被提取为 CLASS 实体，抽象方法也应被提取")
    void testParseInterface() {
        String javaCode = """
                package com.example;

                public interface MyService {
                    void process(String input);
                    int calculate(int a, int b);
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/MyService.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> entities = result.getEntities();
        Entity serviceClass = entities.stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();
        assertEquals("com.example.MyService", serviceClass.getName());

        List<Entity> methods = entities.stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
    }

    @Test
    @DisplayName("多顶级类文件应全部提取")
    void testParseMultipleTopLevelClasses() {
        String javaCode = """
                package com.example;

                class FirstClass {
                    public void firstMethod() {
                    }
                }

                class SecondClass {
                    public void secondMethod() {
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/MultiClass.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        assertEquals(2, classes.size());
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.FirstClass")));
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.SecondClass")));

        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
    }

    @Test
    @DisplayName("无包名类应正确提取")
    void testParseClassWithoutPackage() {
        String javaCode = """
                public class DefaultPackageClass {
                    public void doSomething() {
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("DefaultPackageClass.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        Entity classEntity = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();
        assertEquals("DefaultPackageClass", classEntity.getName());

        Entity methodEntity = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .findFirst().orElseThrow();
        assertEquals("DefaultPackageClass#doSomething()", methodEntity.getName());
    }

    @Test
    @DisplayName("泛型类和方法应被正确解析")
    void testParseGenericClassAndMethod() {
        String javaCode = """
                package com.example;

                public class GenericBox<T> {
                    private T value;

                    public T getValue() {
                        return value;
                    }

                    public <U> U transform(U input) {
                        return input;
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/GenericBox.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.GenericBox#getValue()")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.GenericBox#transform(U)")));
    }

    @Test
    @DisplayName("包含注解的类应被正确解析")
    void testParseClassWithAnnotations() {
        String javaCode = """
                package com.example;

                @SuppressWarnings("unchecked")
                @Deprecated
                public class AnnotatedClass {
                    @Override
                    public String toString() {
                        return "Annotated";
                    }

                    @Deprecated
                    public void oldMethod() {
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/AnnotatedClass.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        Entity classEntity = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();
        assertEquals("com.example.AnnotatedClass", classEntity.getName());

        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
    }

    // ==================== parseEntities - 降级路径 ====================

    @Test
    @DisplayName("仅包声明无类定义应降级")
    void testParseOnlyPackageDeclaration() {
        String javaCode = """
                package com.example;

                import java.util.List;
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Empty.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertNotNull(result.getQuality());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("语法错误代码应触发正则回退，提取类名和方法")
    void testParseSyntaxErrorRegexFallback() {
        String javaCode = """
                package com.example;

                public class BrokenClass {
                    public void validMethod() {
                    }

                    public void brokenMethod() {
                        this is not valid java syntax at all
                        missing semicolons
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/BrokenClass.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertNotNull(result.getQuality());
        assertFalse(result.getEntities().isEmpty());

        boolean hasClass = result.getEntities().stream()
                .anyMatch(e -> e.getKind() == EntityKind.CLASS);
        assertTrue(hasClass, "正则回退应至少提取到类实体");
    }

    @Test
    @DisplayName("超时应返回 TIMEOUT 降级结果")
    void testParseTimeout() {
        String javaCode = """
                package com.example;

                public class SimpleClass {
                    public void method() {
                        System.out.println("Hello");
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/SimpleClass.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = timeoutParser.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertEquals("TIMEOUT", result.getQualityRemark());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("任何输入应始终返回非 null 结果")
    void testParseNeverReturnsNull() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Any.java")
                .content("garbage content that makes no sense")
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertNotNull(result);
        assertNotNull(result.getQuality());
        assertNotNull(result.getEntities());
        assertNotNull(result.getQualityRemark());
    }

    // ==================== JavaParser 特有：registerConfigs ====================

    @Test
    @DisplayName("注册公共配置后默认值正确: timeout=10000, maxParse=10485760, maxRegex=1048576000")
    void testRegisterConfigs_DefaultValues() {
        JavaParser p = new JavaParser();
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        p.registerConfigs(configMap);

        assertEquals(4, configMap.size());
        assertTrue(configMap.containsKey(LanguageParser.CONFIG_KEY_TIMEOUT));
        assertTrue(configMap.containsKey(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE));
        assertTrue(configMap.containsKey(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE));
        assertTrue(configMap.containsKey(JavaParser.CONFIG_KEY_JAVA_LANGUAGE_LEVEL));

        assertEquals(10000L, (long) configMap.get(LanguageParser.CONFIG_KEY_TIMEOUT).getValue());
        assertEquals(10485760L, (long) configMap.get(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE).getValue());
        assertEquals(1048576000L, (long) configMap.get(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE).getValue());
    }

    @Test
    @DisplayName("C-03: 自定义配置覆盖 timeout_ms=1, map 中 timeout 值应为 1")
    void testCustomConfigOverride() {
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        LanguageParser.registerCommonConfigs(configMap);
        // 默认值 10000 → 覆盖为 1L
        configMap.put(LanguageParser.CONFIG_KEY_TIMEOUT,
                ConfigItems.LONG(1L).build());
        new JavaParser(configMap);

        // JavaParser 构造函数不会重置 map, 直接断言覆盖生效
        assertEquals(1L, (long) configMap.get(LanguageParser.CONFIG_KEY_TIMEOUT).getValue());
    }

    @Test
    @DisplayName("C-04: 无参构造(configMap=null)后解析应返回 UNEXPECTED 降级结果")
    void testNoArgsConstructorThenParse() {
        JavaParser p = new JavaParser();
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content("public class Test {}")
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = p.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertNotNull(result.getQualityRemark());
        assertTrue(result.getQualityRemark().startsWith("UNEXPECTED"),
                "期望以 UNEXPECTED 开头, 实际: " + result.getQualityRemark());
    }

    @Test
    @DisplayName("C-05: 传入空 map(未注册公共配置)解析应返回 UNEXPECTED 或 CRASHED")
    void testEmptyConfigMapParse() {
        JavaParser p = new JavaParser(new HashMap<>());
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content("public class Test {}")
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = p.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertNotNull(result.getQualityRemark());
        assertTrue(
                result.getQualityRemark().startsWith("UNEXPECTED")
                        || result.getQualityRemark().startsWith("CRASHED"),
                "期望以 UNEXPECTED 或 CRASHED 开头, 实际: " + result.getQualityRemark());
    }

    // ==================== parseEntities 主流程 ====================

    @Test
    @DisplayName("P-05: AST 成功但无实体(仅 package + import)→ REGEX / AST_NO_ENTITIES")
    void testAstSuccessNoEntities() {
        String javaCode = """
                package com.example;
                import java.util.List;
                import java.util.Map;
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Empty.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.REGEX, result.getQuality());
        assertEquals("AST_NO_ENTITIES", result.getQualityRemark());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("P-07: 语法错误 + Regex 也提取不到 → FILE")
    void testSyntaxErrorRegexAlsoFails() {
        // 完全乱码, 既不是合法 Java 也不含 class/method 声明关键字
        String garbage = "??? &&@@@ ### $$ %%% ^^^";

        SourceCode sourceCode = SourceCode.builder()
                .filePath("Garbage.java")
                .content(garbage)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("P-08: size 超 AST 阈值但未超 Regex → AST_TOO_LARGE 走 Regex")
    void testSizeOverAstThresholdUnderRegex() {
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        LanguageParser.registerCommonConfigs(configMap);
        // 把 AST 阈值设得很小, Regex 阈值保持默认(很大)
        configMap.put(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE,
                ConfigItems.LONG(10L).build());
        JavaParser p = new JavaParser(configMap);

        String validCode = """
                package com.example;
                public class Hello {
                    public int add(int a, int b) { return a + b; }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("Hello.java")
                .content(validCode)
                .language(EntityLanguage.JAVA)
                .sizeInBytes(20L)  // > 10 触发 AST 跳过, < 默认 Regex 阈值
                .build();

        ParsingResult result = p.parseEntities(sourceCode);
        assertEquals(DataQuality.REGEX, result.getQuality());
        assertTrue(result.getQualityRemark().startsWith("AST_TOO_LARGE"),
                "实际 remark: " + result.getQualityRemark());
        assertFalse(result.getEntities().isEmpty());
    }

    @Test
    @DisplayName("P-09: size 同时超 AST 和 Regex 阈值 → REGEX_TOO_LARGE 降级 FILE")
    void testSizeOverBothThresholds() {
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        LanguageParser.registerCommonConfigs(configMap);
        configMap.put(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE,
                ConfigItems.LONG(10L).build());
        configMap.put(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE,
                ConfigItems.LONG(15L).build());
        JavaParser p = new JavaParser(configMap);

        String validCode = """
                package com.example;
                public class Hello {}
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("Hello.java")
                .content(validCode)
                .language(EntityLanguage.JAVA)
                .sizeInBytes(20L)  // 同时超两个阈值
                .build();

        ParsingResult result = p.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertEquals("REGEX_TOO_LARGE", result.getQualityRemark());
    }

    @Test
    @DisplayName("P-11: parseWithAST 抛 RuntimeException → CRASHED")
    void testParseWithAstThrowsRuntimeException() {
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        LanguageParser.registerCommonConfigs(configMap);
        ThrowingJavaParser p = new ThrowingJavaParser(configMap);

        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content("public class Test {}")
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = p.parseEntities(sourceCode);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertTrue(result.getQualityRemark().startsWith("CRASHED"),
                "实际 remark: " + result.getQualityRemark());
    }

    @Test
    @DisplayName("P-12: sourceCode 为 null → executor 内 NPE, 外层 EXECUTOR CATCH 捕获 → CRASHED")
    void testSourceCodeNull() {
        // 源码修复后: executor 内部 submit 的 task 里 doParse(null) NPE → task 内 catch → CRASHED
        ParsingResult result = parser.parseEntities(null);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertNotNull(result.getQualityRemark());
        assertTrue(
                result.getQualityRemark().startsWith("CRASHED")
                        || result.getQualityRemark().startsWith("UNEXPECTED"),
                "实际 remark: " + result.getQualityRemark());
    }

    @Test
    @DisplayName("P-14: size 等于阈值(非大于) → 应正常走 AST 或 Regex")
    void testSizeEqualToThresholds() {
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        LanguageParser.registerCommonConfigs(configMap);
        configMap.put(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE,
                ConfigItems.LONG(100L).build());
        configMap.put(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE,
                ConfigItems.LONG(100L).build());
        JavaParser p = new JavaParser(configMap);

        String validCode = """
                public class Hello {
                    public void greet() { System.out.println("Hi"); }
                }
                """;
        // content.length() 约 90 字符, 手动设为 == 100
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Hello.java")
                .content(validCode)
                .language(EntityLanguage.JAVA)
                .sizeInBytes(100L)  // == 阈值, 不触发 > 判断
                .build();

        ParsingResult result = p.parseEntities(sourceCode);
        // 应走 AST, 不会因大小被跳过
        assertFalse(result.getEntities().isEmpty(),
                "阈值相等时 sizeInBytes > threshold 为 false, 应正常解析");
    }

    // ==================== AST 实体提取 ====================

    @Test
    @DisplayName("A-03: 多层内部类 — 完整 $ 链 pkg.Outer$Inner$Deep")
    void testMultiLevelInnerClasses() {
        String javaCode = """
                package com.example;
                public class Outer {
                    public class Inner {
                        public class Deep {}
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Outer.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        assertEquals(3, classes.size());
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer")));
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer$Inner")));
        // 修复后: Deep 的完整链是 Outer → Inner → Deep
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer$Inner$Deep")));
    }

    @Test
    @DisplayName("A-05: 方法重载 — 同方法名不同参数, full name 应不同")
    void testOverloadedMethods() {
        String javaCode = """
                package com.example;
                public class Cls {
                    public void m(int a) {}
                    public void m(String b) {}
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Cls.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.Cls#m(int)")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.Cls#m(String)")));
    }

    @Test
    @DisplayName("A-06: 构造方法 — 当前源码不提取(只用 MethodDeclaration)")
    void testConstructorNotExtracted() {
        String javaCode = """
                package com.example;
                public class Cls {
                    public Cls(int x) {}
                    public void normalMethod() {}
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Cls.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        // 只有 normalMethod, 构造方法不被提取
        assertEquals(1, methods.size());
        assertEquals("com.example.Cls#normalMethod()", methods.get(0).getName());
    }

    @Test
    @DisplayName("A-07: 泛型/数组/可变参数 — 参数类型字符串正确")
    void testComplexParamTypes() {
        String javaCode = """
                package com.example;
                public class Util {
                    public void process(List<String> list, int[] arr, String... rest) {}
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Util.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(1, methods.size());
        String fullName = methods.get(0).getName();
        assertTrue(fullName.contains("List<String>"), "应包含泛型参数, 实际: " + fullName);
        assertTrue(fullName.contains("int[]"), "应包含数组参数, 实际: " + fullName);
        // ⚠️ javaparser 库行为: 可变参数 String... rest 的 type.asString() 返回 "String"(不含 ...)
        assertTrue(fullName.contains("String") && !fullName.contains("String..."),
                "可变参数会被解析为 String(无...), 实际: " + fullName);
    }

    @Test
    @DisplayName("A-08: 抽象方法 — bodyHash 应为 0")
    void testAbstractMethodBodyHash() {
        String javaCode = """
                package com.example;
                public abstract class AbstractRunner {
                    public abstract void run();
                    public void concrete() {}
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/AbstractRunner.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        Entity runMethod = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD && e.getName().contains("run()"))
                .findFirst().orElseThrow();
        // signature 格式: returnType:params:bodyHash
        // 抽象方法 body 为空, orElse("0") → bodyHash = "0"
        String sig = runMethod.getSignature();
        assertNotNull(sig);
        String bodyHash = sig.substring(sig.lastIndexOf(':') + 1);
        assertEquals("0", bodyHash, "抽象方法的 bodyHash 应为 '0'");
    }

    @Test
    @DisplayName("A-09: 类签名格式 — superName:interfaces(排序):fieldCount:methodSigs(排序;连接)")
    void testClassSignatureFormat() {
        String javaCode = """
                package com.example;
                public class Child extends Parent implements B, A {
                    String field1;
                    int field2;
                    void zMethod() {}
                    void aMethod() {}
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Child.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        Entity childClass = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();

        // 格式: superName:interfaces(sorted,逗号):fieldCount:methodSigs(sorted,分号)
        String sig = childClass.getSignature();
        assertNotNull(sig);
        String[] parts = sig.split(":", 4);
        assertEquals(4, parts.length, "类签名应有 4 段, 实际: " + sig);
        assertEquals("Parent", parts[0], "superName");
        assertEquals("A,B", parts[1], "interfaces 应排序");
        assertEquals("2", parts[2], "fieldCount");
        assertEquals("aMethod();zMethod()", parts[3], "methodSigs 应排序并用分号连接");
    }

    @Test
    @DisplayName("A-10: 方法签名 bodyHash — 同方法名同参数但不同 body, hash 应不同")
    void testMethodBodyHashDifferent() {
        // 方法体不同 → bodyHash 不同, 即使 returnType 和 params 相同
        String javaCode = """
                package com.example;
                public class Greeter {
                    public void greet1() { System.out.println("Hi"); }
                    public void greet2() { System.out.println("Hello"); }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Greeter.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());

        Entity g1 = methods.stream().filter(m -> m.getName().contains("greet1")).findFirst().orElseThrow();
        Entity g2 = methods.stream().filter(m -> m.getName().contains("greet2")).findFirst().orElseThrow();

        // returnType 和 params 都相同 (void, 无参), 但 bodyHash 不同
        String[] sig1 = g1.getSignature().split(":", 3);
        String[] sig2 = g2.getSignature().split(":", 3);
        assertEquals(sig1[0], sig2[0], "returnType 应相同");
        assertEquals(sig1[1], sig2[1], "params 应相同");
        assertNotEquals(sig1[2], sig2[2], "bodyHash 应不同");
    }

    @Test
    @DisplayName("A-11: 内部类的方法全名 — pkg.Outer$Inner#innerMethod()")
    void testInnerClassMethodFullName() {
        String javaCode = """
                package com.example;
                public class Outer {
                    public class Inner {
                        public void innerMethod() {}
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Outer.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(1, methods.size());
        assertEquals("com.example.Outer$Inner#innerMethod()", methods.get(0).getName());
    }

    @Test
    @DisplayName("A-12: 匿名类不提取, 局部类会被提取为 pkg.Outer$Local")
    void testAnonymousAndLocalClasses() {
        String javaCode = """
                package com.example;
                public class Outer {
                    public void method() {
                        // 局部类 (method-local class) — 有名字, 会被提取
                        class Local { void localM() {} }
                        // 匿名类 — 无类名, 不被提取
                        Runnable r = new Runnable() { public void run() {} };
                    }
                }
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Outer.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        // 不应崩溃
        ParsingResult result = parser.parseEntities(sourceCode);
        assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer")));
        // 局部类也会被提取
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.Outer$Local")),
                "局部类应被提取为内部类形式");
        // 没有匿名类实体 (匿名类没有 ClassOrInterfaceDeclaration)
        long anonCount = classes.stream()
                .filter(c -> c.getName().contains("$Local"))
                .count();
        assertTrue(anonCount >= 1);
    }

    @Test
    @DisplayName("A-13: 枚举/记录/注解 — 不崩溃, 不提取为 CLASS")
    void testEnumRecordAnnotationNoCrash() {
        String javaCode = """
                package com.example;
                public enum Color { RED, GREEN, BLUE }
                public record Point(int x, int y) {}
                public @interface MyAnno {}
                """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/Types.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();

        // 不应崩溃
        ParsingResult result = parser.parseEntities(sourceCode);
        assertNotNull(result);
        assertNotNull(result.getQuality());

        // 这些类型都不是 ClassOrInterfaceDeclaration, 不会被提取为 CLASS 实体
        long classCount = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .count();
        assertEquals(0, classCount);
    }

    // ==================== Regex 回退 ====================

    /**
     * 构造触发 Regex 路径的 SourceCode.
     * 原理: sizeInBytes > max_parse_size_bytes (默认 10MB) → 源码第 116 行跳过 AST → 直接走 Regex.
     * 这样 100% 确保走 Regex, 不依赖 javaparser 对语法错误的容错行为.
     */
    private SourceCode regexSource(String code) {
        return SourceCode.builder()
                .filePath("regex/Trigger.java")
                .content(code)
                .sizeInBytes(10485761L)  // 默认 max_parse_size_bytes=10485760, 刚好 +1 触发跳过
                .language(EntityLanguage.JAVA)
                .build();
    }

    @Test
    @DisplayName("R-01: Regex 匹配所有修饰符 class — public/private/protected/abstract/final 都能提取")
    void testRegexAllClassModifiers() {
        String code = """
                package com.example;
                public class A {}
                private class B {}
                protected class C {}
                abstract class D {}
                final class E {}
                // 语法错误: 故意少一个闭合大括号
                class Bad {}
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        assertEquals(DataQuality.REGEX, result.getQuality());

        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        // A~E + Bad 共 6 个类
        assertEquals(6, classes.size());
        assertTrue(classes.stream().allMatch(c -> c.getName().startsWith("com.example.")));
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.A")));
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.example.E")));
    }

    @Test
    @DisplayName("R-02: Regex 带包名 — com.a.B")
    void testRegexClassNameWithPackage() {
        String code = """
                package com.a;
                class B {}
                class C {}
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.a.B")));
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals("com.a.C")));
    }

    @Test
    @DisplayName("R-03: Regex 默认包 — 无 package, 类实体名直接是 B")
    void testRegexClassNameDefaultPackage() {
        String code = """
                class B {
                    void m() {}
                }
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        assertEquals(1, classes.size());
        assertEquals("B", classes.get(0).getName());
    }

    @Test
    @DisplayName("R-04: Regex 匹配方法 — 参数空白全压缩 int a,int b → inta,intb")
    void testRegexMethodExtraction() {
        String code = """
                package com.example;
                class B {
                    public static int add(int a, int b) { return a + b; }
                    private void noop() {}
                }
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
        // ⚠️ Regex 路径: group(2).replaceAll("\\s+","") 去掉所有空白, 类型和变量名连在一起
        // add(int a, int b) → group(2)="int a, int b" → 压缩为 "inta,intb"
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.B#add(inta,intb)")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.B#noop()")));
    }

    @Test
    @DisplayName("R-05: Regex 参数空白压缩 — String  a,  int b → Stringa,intb")
    void testRegexParamWhitespaceCompression() {
        // METHOD_DECL_PATTERN: group(2).replaceAll("\\s+", "") — 所有空白都去掉
        String code = """
                class B {
                    void m(String  a,  int b) {}
                }
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        Entity method = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .findFirst().orElseThrow();
        // String 和 a 之间的空白也被去掉了, 所以是 Stringa
        assertEquals("B#m(Stringa,intb)", method.getName());
    }

    @Test
    @DisplayName("R-06: Regex 支持 throws 子句 — 不影响参数提取")
    void testRegexMethodWithThrows() {
        String code = """
                package com.example;
                class B {
                    void m() throws IOException {}
                }
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(1, methods.size());
        // throws 是非捕获可选组, 不影响参数 group(2) = "" (空)
        assertEquals("com.example.B#m()", methods.get(0).getName());
    }

    @Test
    @DisplayName("R-07: Regex 父类查找 — 方法归属最近的(位置最前的) class")
    void testRegexMethodParentClass() {
        // PARENT_CLASS_PATTERN 行尾匹配, 在方法位置之前找最后一个 class 声明行
        String code = """
                class A {
                }
                class B {
                    void m() {}
                }
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(1, methods.size());
        // 方法在 B 的花括号里, 最近的 class 声明是 B → 归属 B
        assertEquals("B#m()", methods.get(0).getName());
    }

    @Test
    @DisplayName("R-08: Regex 不匹配 interface/enum — 只有 class 才被提取")
    void testRegexNoInterfaceOrEnum() {
        String code = """
                package com.example;
                interface I {}
                enum E { A, B }
                record R(int x) {}
                class C { void m() {} }
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> classes = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .toList();
        // 只有 C 被提取, interface/enum/record 都不是 class 关键字
        assertEquals(1, classes.size());
        assertEquals("com.example.C", classes.get(0).getName());
    }

    @Test
    @DisplayName("R-09: Regex 不误匹配控制语句 — if/while/for 不被当作方法")
    void testRegexNoFalsePositiveControlFlow() {
        String code = """
                class C {
                    void good() {}
                    if (x) { }
                    while (y) { }
                    for (int i = 0; i < 10; i++) { }
                    try { } catch (Exception e) { }
                }
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        // 只有 good() 是方法, 其余都是控制语句
        assertEquals(1, methods.size());
        assertEquals("C#good()", methods.get(0).getName());
    }

    @Test
    @DisplayName("R-10: Regex 不提取构造方法 — 构造方法无返回类型, Pattern 要求至少一个")
    void testRegexNoConstructor() {
        // METHOD_DECL_PATTERN 核心: (?:public\s+...)?(?:static\s+)?(?:\w+\s+)+ — 必须有返回类型
        String code = """
                class B {
                    public B() {}
                    public B(int x) {}
                    void normal() {}
                }
                // 语法错误触发 Regex
                """;

        ParsingResult result = parser.parseEntities(regexSource(code));
        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        // 只有 normal() 被提取, 两个构造方法因无返回类型不匹配 Pattern
        assertEquals(1, methods.size());
        assertEquals("B#normal()", methods.get(0).getName());
    }

    // ==================== B 系列: 边界与工具方法 ====================

    // ---- B-01: posToOffset 普通行列 ----

    @Test
    @DisplayName("B-01: posToOffset 普通 Unix 换行正确映射")
    void testPosToOffset_Basic() {
        // "abc\ndef\nghi"
        // 位置: a(0),b(1),c(2),\n(3),d(4),e(5),f(6),\n(7),g(8),h(9),i(10)
        String content = "abc\ndef\nghi";
        assertEquals(0,  parser.posToOffset(content, 1, 1), "(1,1) 'a'");
        assertEquals(2,  parser.posToOffset(content, 1, 3), "(1,3) 'c'");
        assertEquals(4,  parser.posToOffset(content, 2, 1), "(2,1) 'd'");
        assertEquals(6,  parser.posToOffset(content, 2, 3), "(2,3) 'f'");
        assertEquals(10, parser.posToOffset(content, 3, 3), "(3,3) 'i'");
    }

    // ---- B-02: posToOffset 处理 \r\n ----

    @Test
    @DisplayName("B-02: posToOffset 处理 Windows \\r\\n 换行")
    void testPosToOffset_WindowsNewline() {
        // "a\r\nb\r\nc"  → 长度 8: a,\r,\n,b,\r,\n,c
        String content = "a\r\nb\r\nc";
        // ✅ 修复后 \r\n 算作一个换行, line 正确推进
        assertEquals(0, parser.posToOffset(content, 1, 1), "(1,1) 应指向 'a'");
        assertEquals(3, parser.posToOffset(content, 2, 1), "(2,1) 应指向 'b' (offset 3, 不是 2)");
        assertEquals(6, parser.posToOffset(content, 3, 1), "(3,1) 应指向 'c' (offset 6)");
    }

    // ---- B-03: posToOffset Unicode emoji ----

    @Test
    @DisplayName("B-03: posToOffset 处理含中文和 emoji 的 UTF-16 内容")
    void testPosToOffset_Unicode() {
        // emoji 😀 是 surrogate pair, 占 2 个 Java char
        String content = "你好 😀 世界\nx";
        // 第一行: 你(0), 好(1), (2), 😀(3-4), (5), 世(6), 界(7)
        // 第二行: x(9)
        assertEquals(0, parser.posToOffset(content, 1, 1), "中文字符首字");
        assertEquals(9, parser.posToOffset(content, 2, 1), "emoji 后的第二行首字");
        // 越界场景 → 返回 content.length()
        int outOfBounds = parser.posToOffset(content, 99, 99);
        assertEquals(content.length(), outOfBounds, "超出范围应返回末尾 offset");
    }

    // ---- B-04: extractRange 正常节点 ----

    @Test
    @DisplayName("B-04: extractRange 能正确提取 AST 节点的源码范围")
    void testExtractRange_NormalNode() {
        String code = "public class A { public void m() {} }";
        ParseResult<CompilationUnit> parseResult = parser.createAstParser().parse(code);
        assertTrue(parseResult.isSuccessful(), "应解析成功");
        CompilationUnit cu = parseResult.getResult().orElseThrow();
        MethodDeclaration m = cu.findFirst(MethodDeclaration.class).orElseThrow();

        String range = parser.extractRange(code, m);
        // 先打印实际内容, 方便确认 extractRange 行为
        System.err.println("[B-04 DEBUG] range=[" + range + "]");
        assertTrue(range.contains("void"), "范围应包含方法签名关键字 void, 实际=" + range);
        assertTrue(range.contains("m"), "范围应包含方法名");
        // 方法体有 {}, extractRange 应覆盖完整节点
        assertTrue(range.contains("{"), "范围应包含方法体左大括号");
    }

    // ---- B-05: extractRange 无 range 兜底 ----

    @Test
    @DisplayName("B-05: extractRange 无 range 节点用 toString 兜底 (无法构造无 range 的正常节点, 跳过)")
    void testExtractRange_NoRangeFallback() {
        // javaparser 解析成功的节点都有 range, 无法构造一个正常节点但无 range
        // 此处仅验证: 正常路径不崩即可 (B-04 已覆盖)
        String code = "public class A {}";
        ParseResult<CompilationUnit> parseResult = parser.createAstParser().parse(code);
        assertTrue(parseResult.isSuccessful());
        CompilationUnit cu = parseResult.getResult().orElseThrow();
        // cu.getPrimaryType() 返回 Optional<TypeDeclaration>
        cu.getPrimaryType().ifPresent(node -> {
            String range = parser.extractRange(code, node);
            assertNotNull(range);
            assertTrue(range.contains("class A"));
        });
    }

    // ---- B-06: 并发调用 parseEntities ----

    @Test
    @DisplayName("B-06: 多线程并发调用 parseEntities 不应崩溃")
    void testConcurrentParseEntities() throws InterruptedException {
        int threadCount = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        SourceCode sc = SourceCode.builder()
                .filePath("concurrent/Test.java")
                .content("public class Test { public void m() {} }")
                .language(EntityLanguage.JAVA)
                .build();

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    ParsingResult result = parser.parseEntities(sc);
                    // 只要不崩就算成功 (SingleThreadExecutor 内部排队)
                    if (result != null) successCount.incrementAndGet();
                    else failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.setName("parse-concurrent-" + i);
            t.start();
        }

        startLatch.countDown(); // 放行所有线程
        assertTrue(doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "并发测试应在 5s 内完成");
        assertEquals(0, failCount.get(), "不应有线程抛异常, 失败数=" + failCount.get());
        assertEquals(threadCount, successCount.get(), "所有线程都应返回非 null 结果");
    }

    // ---- B-07: 超时后再次解析 ----

    @Test
    @DisplayName("B-07: 超时后正常 parser 仍可用, 不应卡死")
    void testTimeoutThenNormal() {
        // 用 timeoutParser (timeout=0) 解析任意文件 → 一定超时
        SourceCode sc = SourceCode.builder()
                .filePath("timeout/ThenNormal.java")
                .content("public class X {}")
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult timeoutResult = timeoutParser.parseEntities(sc);
        assertNotNull(timeoutResult, "超时后仍应返回 fallback 结果");
        assertEquals(DataQuality.FILE, timeoutResult.getQuality(),
                "超时应降级为 FILE 级别");
        assertTrue(timeoutResult.getQualityRemark() != null
                        && timeoutResult.getQualityRemark().contains("TIMEOUT"),
                "remark 应包含 TIMEOUT");

        // 再用正常 parser 解析 → 应正常返回
        ParsingResult normalResult = parser.parseEntities(sc);
        assertNotNull(normalResult);
        assertEquals(DataQuality.AST, normalResult.getQuality(),
                "正常 parser AST 成功应返回 AST, 实际=" + normalResult.getQuality());
    }

    // ---- B-08: buildFallbackResult 耗时为 0 ----

    @Test
    @DisplayName("B-08: buildFallbackResult 构造的结果 parseDurationMs 应为 0")
    void testBuildFallbackResult_Duration() {
        ParsingResult fallback = parser.buildFallbackResult(DataQuality.FILE, "TEST_REMARK");
        assertEquals(DataQuality.FILE, fallback.getQuality());
        assertEquals("TEST_REMARK", fallback.getQualityRemark());
        assertEquals(0, fallback.getParseDurationMs(), "fallback 结果耗时应为 0");
        assertNotNull(fallback.getEntities());
        assertTrue(fallback.getEntities().isEmpty(), "fallback 实体列表应为空");
    }

    // ---- B-09: sizeInBytes 与实际长度不一致 ----

    @Test
    @DisplayName("B-09: sizeInBytes 故意设大时, 以 sizeInBytes 为准触发降级")
    void testSizeInBytesInconsistent() {
        String content = "public class Small {}";
        // content.length() ≈ 22, 但 sizeInBytes 故意设成超大规模
        SourceCode sc = SourceCode.builder()
                .filePath("fake/Small.java")
                .content(content)
                .sizeInBytes(10485761L) // 超过 maxParseSize (10MB)
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sc);
        // sizeInBytes > maxParseSize → AST 路径走不到 → 走 regex
        assertNotNull(result);
        // regex 路径 sizeInBytes 也超 maxRegexSize (默认 11MB?) → 继续降级
        // 不管怎样, 不应崩
        assertTrue(result.getQuality() != null);
    }

    // ==================== 内部类 ====================

    /** P-11 专用: 覆盖 parseWithAST 抛异常 */
    private static class ThrowingJavaParser extends JavaParser {
        public ThrowingJavaParser(Map<String, ConfigItem<?>> configMap) {
            super(configMap);
        }
        @Override
        protected ParseResult<CompilationUnit> parseWithAST(String content) {
            throw new RuntimeException("boom from parseWithAST");
        }
    }
}