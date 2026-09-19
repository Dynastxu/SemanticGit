package com.github.semanticgit.parser.java;

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
    @DisplayName("registerConfigs 应注册三个通用配置项")
    void testRegisterConfigs() {
        JavaParser p = new JavaParser();
        Map<String, ConfigItem<?>> configMap = new HashMap<>();
        p.registerConfigs(configMap);

        assertTrue(configMap.containsKey(LanguageParser.CONFIG_KEY_TIMEOUT));
        assertTrue(configMap.containsKey(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE));
        assertTrue(configMap.containsKey(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE));
        assertEquals(3, configMap.size());
    }
}
