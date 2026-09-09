package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.api.ParsingResult;
import com.github.semanticgit.parser.java.api.SourceCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserRegistryTest {

    private final ParserRegistry registry = new ParserRegistry();

    @Test
    @DisplayName("detectLanguage: .java → JAVA")
    void detectLanguage_Java() {
        assertEquals(EntityLanguage.JAVA, ParserRegistry.detectLanguage("src/main/java/Foo.java"));
        assertEquals(EntityLanguage.JAVA, ParserRegistry.detectLanguage("Foo.JAVA"));
    }

    @Test
    @DisplayName("detectLanguage: .py → PYTHON")
    void detectLanguage_Python() {
        assertEquals(EntityLanguage.PYTHON, ParserRegistry.detectLanguage("module.py"));
    }

    @Test
    @DisplayName("detectLanguage: .js → JS")
    void detectLanguage_Js() {
        assertEquals(EntityLanguage.JS, ParserRegistry.detectLanguage("script.js"));
    }

    @Test
    @DisplayName("detectLanguage: .ts/.tsx → TS")
    void detectLanguage_Ts() {
        assertEquals(EntityLanguage.TS, ParserRegistry.detectLanguage("component.ts"));
        assertEquals(EntityLanguage.TS, ParserRegistry.detectLanguage("component.tsx"));
    }

    @Test
    @DisplayName("detectLanguage: 不支持的文件扩展名 → null")
    void detectLanguage_Unsupported() {
        assertNull(ParserRegistry.detectLanguage("README.md"));
        assertNull(ParserRegistry.detectLanguage("build.xml"));
        assertNull(ParserRegistry.detectLanguage("pom.xml"));
        assertNull(ParserRegistry.detectLanguage("Dockerfile"));
    }

    @Test
    @DisplayName("detectLanguage: null 路径 → null")
    void detectLanguage_NullPath() {
        assertNull(ParserRegistry.detectLanguage(null));
    }

    @Test
    @DisplayName("parse: Java 源码应成功解析出实体")
    void parse_JavaSource_ReturnsEntities() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("src/main/java/com/example/Hello.java")
                .content("""
                    package com.example;
                    
                    public class Hello {
                        public void greet() {
                            System.out.println("Hello");
                        }
                        
                        public String getName() {
                            return "World";
                        }
                    }
                    """)
                .language(EntityLanguage.JAVA)
                .sizeInBytes(200)
                .build();

        ParsingResult result = registry.parse(sourceCode);

        assertNotNull(result);
        assertEquals(DataQuality.AST, result.getQuality());
        assertFalse(result.getEntities().isEmpty(), "应解析出实体");

        boolean hasClass = result.getEntities().stream()
                .anyMatch(e -> "com.example.Hello".equals(e.getName()));
        boolean hasGreet = result.getEntities().stream()
                .anyMatch(e -> "com.example.Hello#greet".equals(e.getName()));
        boolean hasGetName = result.getEntities().stream()
                .anyMatch(e -> "com.example.Hello#getName".equals(e.getName()));

        assertTrue(hasClass, "应包含 Hello 类");
        assertTrue(hasGreet, "应包含 greet 方法");
        assertTrue(hasGetName, "应包含 getName 方法");
    }

    @Test
    @DisplayName("parse: 空 Java 源码应返回空实体列表")
    void parse_EmptyContent_ReturnsEmptyEntities() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Empty.java")
                .content("")
                .language(EntityLanguage.JAVA)
                .sizeInBytes(0)
                .build();

        ParsingResult result = registry.parse(sourceCode);

        assertNotNull(result);
        assertTrue(result.getEntities().isEmpty(), "空文件不应有实体");
    }

    @Test
    @DisplayName("parse: 语法错误的 Java 源码应降级到 REGEX 或 FILE")
    void parse_InvalidJavaSyntax_Fallback() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("Broken.java")
                .content("this is not valid java at all {{{")
                .language(EntityLanguage.JAVA)
                .sizeInBytes(50)
                .build();

        ParsingResult result = registry.parse(sourceCode);

        assertNotNull(result);
        assertNotEquals(DataQuality.AST, result.getQuality(), "语法错误不应返回 AST 质量");
        assertNotNull(result.getQualityRemark());
    }

    @Test
    @DisplayName("parse: 不支持的语言应返回 FILE 级兜底")
    void parse_UnsupportedLanguage_ReturnsFileFallback() {
        SourceCode sourceCode = SourceCode.builder()
                .filePath("test.py")
                .content("def foo():\n    pass\n")
                .language(EntityLanguage.PYTHON)
                .sizeInBytes(30)
                .build();

        ParsingResult result = registry.parse(sourceCode);

        assertNotNull(result);
        assertEquals(DataQuality.FILE, result.getQuality());
        assertTrue(result.getEntities().isEmpty());
        assertTrue(result.getQualityRemark().contains("NO_PARSER"));
    }
}
