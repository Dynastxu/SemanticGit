package com.github.semanticgit.parser.java;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.api.EntityChange;
import com.github.semanticgit.parser.java.api.SourceCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaParserChangeNatureTest {

    private JavaParser parser;

    @BeforeEach
    void setUp() {
        parser = new JavaParser(JavaParserConfig.builder().build());
    }

    // ==================== parseEntityChangeNatureFlag ====================

    @Test
    @DisplayName("before 为 null → FEAT")
    void testEntityChangeNatureFlag_BeforeNull() {
        int flags = parser.parseEntityChangeNatureFlag(null, "int add() { return 1; }");
        assertTrue((flags & ChangeNatureFlag.FEAT.code) != 0);
    }

    @Test
    @DisplayName("after 为 null → FEAT")
    void testEntityChangeNatureFlag_AfterNull() {
        int flags = parser.parseEntityChangeNatureFlag("int add() { return 1; }", null);
        assertTrue((flags & ChangeNatureFlag.FEAT.code) != 0);
    }

    @Test
    @DisplayName("前后都为空 → 0")
    void testEntityChangeNatureFlag_BothEmpty() {
        assertEquals(0, parser.parseEntityChangeNatureFlag(null, null));
        assertEquals(0, parser.parseEntityChangeNatureFlag("", ""));
    }

    @Test
    @DisplayName("完全相同 → 0")
    void testEntityChangeNatureFlag_Identical() {
        String code = "int add(int a, int b) { return a + b; }";
        assertEquals(0, parser.parseEntityChangeNatureFlag(code, code));
    }

    @Test
    @DisplayName("仅注释不同 → DOCS")
    void testEntityChangeNatureFlag_DocsOnly() {
        String before = "int add(int a, int b) { return a + b; }";
        String after  = "// updated comment\n  int add(int a, int b) { return a + b; }";
        int flags = parser.parseEntityChangeNatureFlag(before, after);
        assertEquals(ChangeNatureFlag.DOCS.code, flags);
    }

    @Test
    @DisplayName("仅空白不同 → STYLE")
    void testEntityChangeNatureFlag_StyleOnly() {
        String before = "int add(int a,int b){return a+b;}";
        String after  = "int add(int a, int b) {\n    return a + b;\n}";
        int flags = parser.parseEntityChangeNatureFlag(before, after);
        assertEquals(ChangeNatureFlag.STYLE.code, flags);
    }

    @Test
    @DisplayName("方法体显著缩小 → FIX")
    void testEntityChangeNatureFlag_Fix() {
        String before = "void process() { int x = 1; int y = 2; int z = 3; System.out.println(x + y + z); }";
        String after  = "void process() { return; }";
        int flags = parser.parseEntityChangeNatureFlag(before, after);
        assertTrue((flags & ChangeNatureFlag.FIX.code) != 0);
    }

    @Test
    @DisplayName("方法体变大/改变 → FEAT")
    void testEntityChangeNatureFlag_Feat() {
        String before = "void greet() { System.out.println(\"Hi\"); }";
        String after  = "void greet() { System.out.println(\"Hello, World!\"); }";
        int flags = parser.parseEntityChangeNatureFlag(before, after);
        assertTrue((flags & ChangeNatureFlag.FEAT.code) != 0);
    }

    // ==================== parseChangeNatureFlags ====================

    @Test
    @DisplayName("新增实体 → before=null, FEAT")
    void testChangeNatureFlags_AddedEntity() {
        String code = """
                package com.example;
                public class Service {
                    public String greet(String name) {
                        return "Hi, " + name;
                    }
                }
                """;

        SourceCode after = sourceCode("Service.java", code);

        List<EntityChange> changes = parser.parseChangeNatureFlags(null, after);
        assertEquals(2, changes.size());

        for (EntityChange c : changes) {
            assertNull(c.getBefore());
            assertNotNull(c.getAfter());
            assertTrue((c.getFlags() & ChangeNatureFlag.FEAT.code) != 0);
        }
    }

    @Test
    @DisplayName("删除实体 → after=null, FEAT")
    void testChangeNatureFlags_RemovedEntity() {
        String code = """
                package com.example;
                public class Service {
                    public void process() {
                    }
                }
                """;

        SourceCode before = sourceCode("Service.java", code);

        List<EntityChange> changes = parser.parseChangeNatureFlags(before, null);
        assertEquals(2, changes.size());

        for (EntityChange c : changes) {
            assertNotNull(c.getBefore());
            assertNull(c.getAfter());
            assertTrue((c.getFlags() & ChangeNatureFlag.FEAT.code) != 0);
        }
    }

    @Test
    @DisplayName("仅注释变更 → DOCS")
    void testChangeNatureFlags_DocsChange() {
        String beforeCode = """
                package com.example;
                public class Calc {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

        String afterCode = """
                package com.example;
                // 加法
                /** @return 两数之和 */
                public class Calc {
                    public int add(int a, int b) {
                        // calc sum
                        return a + b;
                    }
                }
                """;

        SourceCode before = sourceCode("Calc.java", beforeCode);
        SourceCode after = sourceCode("Calc.java", afterCode);

        List<EntityChange> changes = parser.parseChangeNatureFlags(before, after);
        assertEquals(2, changes.size());

        for (EntityChange c : changes) {
            assertNotNull(c.getBefore());
            assertNotNull(c.getAfter());
            assertEquals(ChangeNatureFlag.DOCS.code, c.getFlags());
        }
    }

    @Test
    @DisplayName("仅格式变更 → STYLE")
    void testChangeNatureFlags_StyleChange() {
        String beforeCode = """
                package com.example;
                public class Calc{public int add(int a,int b){return a+b;}}
                """;

        String afterCode = """
                package com.example;
                public class Calc {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

        SourceCode before = sourceCode("Calc.java", beforeCode);
        SourceCode after = sourceCode("Calc.java", afterCode);

        List<EntityChange> changes = parser.parseChangeNatureFlags(before, after);
        assertEquals(2, changes.size());

        for (EntityChange c : changes) {
            assertNotNull(c.getBefore());
            assertNotNull(c.getAfter());
            assertEquals(ChangeNatureFlag.STYLE.code, c.getFlags());
        }
    }

    @Test
    @DisplayName("测试文件应叠加 TEST 标记")
    void testChangeNatureFlags_TestFileFlag() {
        String code = """
                package com.example;
                public class CalcTest {
                    public void testAdd() {
                    }
                }
                """;

        SourceCode after = sourceCode("src/test/java/CalcTest.java", code);

        List<EntityChange> changes = parser.parseChangeNatureFlags(null, after);
        for (EntityChange c : changes) {
            assertTrue((c.getFlags() & ChangeNatureFlag.TEST.code) != 0);
        }
    }

    @Test
    @DisplayName("重载方法应被识别为不同实体")
    void testChangeNatureFlags_OverloadedMethods() {
        String beforeCode = """
                package com.example;
                public class Util {
                    public String format(int n) {
                        return String.valueOf(n);
                    }
                    public String format(double d) {
                        return String.valueOf(d);
                    }
                }
                """;

        String afterCode = """
                package com.example;
                public class Util {
                    public String format(int n) {
                        return "int: " + n;
                    }
                    public String format(double d) {
                        return String.valueOf(d);
                    }
                }
                """;

        SourceCode before = sourceCode("Util.java", beforeCode);
        SourceCode after = sourceCode("Util.java", afterCode);

        List<EntityChange> changes = parser.parseChangeNatureFlags(before, after);

        EntityChange intFormat = changes.stream()
                .filter(c -> c.getAfter() != null
                        && c.getAfter().getName().contains("format(int)"))
                .findFirst().orElseThrow();
        assertTrue((intFormat.getFlags() & ChangeNatureFlag.FEAT.code) != 0);

        EntityChange doubleFormat = changes.stream()
                .filter(c -> c.getAfter() != null
                        && c.getAfter().getName().contains("format(double)"))
                .findFirst().orElseThrow();
        assertEquals(0, doubleFormat.getFlags());
    }

    // ==================== helper ====================

    private SourceCode sourceCode(String filePath, String content) {
        return SourceCode.builder()
                .filePath(filePath)
                .content(content)
                .language(EntityLanguage.JAVA)
                .sizeInBytes(content.length())
                .build();
    }
}
