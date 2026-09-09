package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.core.parser.ParserRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserRegistryTest {
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
}
