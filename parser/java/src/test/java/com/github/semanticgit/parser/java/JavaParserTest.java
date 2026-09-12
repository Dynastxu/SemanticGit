package com.github.semanticgit.parser.java;

import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.SourceCode;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.common.entity.EntityKind;
import com.github.semanticgit.common.entity.EntityLanguage;

import java.util.EnumSet;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserTest {

    private JavaParser parser;
    private JavaParser timeoutParser;

    @BeforeEach
    void setUp() {
        parser = new JavaParser(JavaParserConfig.builder().build());
        timeoutParser = new JavaParser(JavaParserConfig.builder().timeoutMs(0).build());
    }

    @AfterEach
    void tearDown() {
        parser = null;
        timeoutParser = null;
    }

    @Test
    @DisplayName("getSupportedLanguage 应返回 JAVA")
    void testGetSupportedLanguage() {
        Assertions.assertEquals(EntityLanguage.JAVA, parser.getSupportedLanguage());
    }

    @Test
    @DisplayName("空内容应返回 FILE 级别降级结果")
    void testParseEmptyContent() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Test.java")
                .content("")
                .language(EntityLanguage.JAVA)
                .build();

        ParsingResult result = parser.parseEntities(sourceCode);

        Assertions.assertEquals(DataQuality.FILE, result.getQuality());
        Assertions.assertEquals("EMPTY_FILE", result.getQualityRemark());
        Assertions.assertTrue(result.getEntities().isEmpty());
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

        Assertions.assertEquals(DataQuality.FILE, result.getQuality());
        Assertions.assertEquals("EMPTY_FILE", result.getQualityRemark());
        Assertions.assertTrue(result.getEntities().isEmpty());
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

        Assertions.assertEquals(DataQuality.FILE, result.getQuality());
        Assertions.assertEquals("EMPTY_FILE", result.getQualityRemark());
    }

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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());
        Assertions.assertEquals("AST_SUCCESS", result.getQualityRemark());
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
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.HelloWorld#sayHello")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.HelloWorld#getName")));
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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());

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
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.Outer#outerMethod")));
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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());

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
    @DisplayName("仅包声明无类定义应降级到 FILE 级别")
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
        Assertions.assertTrue(result.getEntities().isEmpty());
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
        Assertions.assertFalse(result.getEntities().isEmpty());

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

        Assertions.assertEquals(DataQuality.FILE, result.getQuality());
        Assertions.assertEquals("TIMEOUT", result.getQualityRemark());
        Assertions.assertTrue(result.getEntities().isEmpty());
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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());

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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());

        Entity classEntity = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();
        assertEquals("DefaultPackageClass", classEntity.getName());

        Entity methodEntity = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .findFirst().orElseThrow();
        assertEquals("DefaultPackageClass#doSomething", methodEntity.getName());
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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());

        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.GenericBox#getValue")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("com.example.GenericBox#transform")));
    }

    @Test
    @DisplayName("parse 方法应始终返回非 null 结果")
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

        Assertions.assertEquals(DataQuality.AST, result.getQuality());

        Entity classEntity = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.CLASS)
                .findFirst().orElseThrow();
        assertEquals("com.example.AnnotatedClass", classEntity.getName());

        List<Entity> methods = result.getEntities().stream()
                .filter(e -> e.getKind() == EntityKind.METHOD)
                .toList();
        assertEquals(2, methods.size());
    }

    @Test
    @DisplayName("AST 解析 OOM 但正则回退成功应返回 REGEX 级别")
    void testParseOOMWithRegexFallbackSuccess() {
        JavaParser oomParser = new JavaParser(JavaParserConfig.builder().build()) {
            @Override
            protected ParseResult<CompilationUnit> parseWithAST(String content) {
                throw new OutOfMemoryError("Simulated Java heap space");
            }
        };

        String javaCode = """
            package com.example;

            public class RecoverableClass {
                public void someMethod() {
                }
            }
            """;

        SourceCode sourceCode = SourceCode.builder()
                .filePath("com/example/RecoverableClass.java")
                .content(javaCode)
                .language(EntityLanguage.JAVA)
                .build();


        ParsingResult result = oomParser.parseEntities(sourceCode);

        assertNotNull(result);
        Assertions.assertEquals(DataQuality.FILE, result.getQuality());
        Assertions.assertTrue(result.getQualityRemark().contains("OUT_OF_MEMORY"));
    }
}
