package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.common.entity.EntityKind;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.common.entity.AnalysisType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisEngineFullAnalysisTest {
    private AnalysisEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AnalysisEngine();
    }

    static CommitMeta dummyCommit() {
        return CommitMeta.builder()
                .hash("abc123")
                .message("test commit")
                .timestamp(0)
                .build();
    }

    static ChangeLog removal(String filePath, Entity entity, int flags) {
        return ChangeLog.builder()
                .commit(dummyCommit())
                .entity(entity)
                .filePath(filePath)
                .operation(ChangeOperation.REMOVE)
                .natureFlagCode(flags)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();
    }

    static ChangeLog addition(String filePath, Entity entity, int flags) {
        return ChangeLog.builder()
                .commit(dummyCommit())
                .entity(entity)
                .filePath(filePath)
                .operation(ChangeOperation.ADD)
                .natureFlagCode(flags)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();
    }

    static ChangeLog modify(String filePath, Entity entity, int flags) {
        return ChangeLog.builder()
                .commit(dummyCommit())
                .entity(entity)
                .filePath(filePath)
                .operation(ChangeOperation.MODIFY)
                .natureFlagCode(flags)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();
    }

    static Entity classEntity(String name, String signature) {
        return Entity.builder()
                .name(name)
                .kind(EntityKind.CLASS)
                .language(EntityLanguage.JAVA)
                .signature(signature)
                .build();
    }

    static Entity methodEntity(String name, String signature) {
        return Entity.builder()
                .name(name)
                .kind(EntityKind.METHOD)
                .language(EntityLanguage.JAVA)
                .signature(signature)
                .build();
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 相同签名的 CLASS 跨文件移动 → 匹配为 REFACTOR")
    void classMove_Matched() {
        String sig = "ArrayList:Serializable:3:add(E);get(int);size()";
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("old/Foo.java", classEntity("com.old.Foo", sig), ChangeNatureFlag.FEAT.code));
        input.add(addition("new/Foo.java", classEntity("com.new.Foo", sig), ChangeNatureFlag.FEAT.code));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
        ChangeLog matched = result.getFirst();
        assertEquals(ChangeOperation.MODIFY, matched.getOperation());
        assertEquals("new/Foo.java", matched.getFilePath());
        assertEquals("com.new.Foo", matched.getEntity().getName());
        assertEquals("com.old.Foo", matched.getParentEntity().getName());
        assertEquals(ChangeNatureFlag.FEAT.code | ChangeNatureFlag.REFACTOR.code, matched.getNatureFlagCode());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 相同签名的 METHOD 跨文件移动 → 匹配为 REFACTOR")
    void methodMove_Matched() {
        String sig = "void:int,String:a1b2c3d4";
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("old/Utils.java", methodEntity("com.old.Utils#process(int,String)", sig), 0));
        input.add(addition("new/Utils.java", methodEntity("com.new.Utils#process(int,String)", sig), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
        ChangeLog matched = result.getFirst();
        assertEquals(ChangeOperation.MODIFY, matched.getOperation());
        assertEquals(ChangeNatureFlag.REFACTOR.code, matched.getNatureFlagCode());
        assertEquals("com.old.Utils#process(int,String)", matched.getParentEntity().getName());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 同文件内的 REMOVE+ADD → 不匹配")
    void sameFile_NotMatched() {
        String sig = "void::0";
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Same.java", methodEntity("com.foo.Same#m()", sig), 0));
        input.add(addition("Same.java", methodEntity("com.foo.Same#m()", sig), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getOperation() == ChangeOperation.REMOVE));
        assertTrue(result.stream().anyMatch(c -> c.getOperation() == ChangeOperation.ADD));
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 不同实体类型 → 不匹配")
    void differentKind_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Foo.java", classEntity("com.old.Foo", ": :0:method()"), 0));
        input.add(addition("Bar.java", methodEntity("com.new.Bar#method()", "void::0"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 签名为 null → 不匹配")
    void nullSignature_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", Entity.builder().name("com.old.Foo").kind(EntityKind.CLASS).build(), 0));
        input.add(addition("New.java", Entity.builder().name("com.new.Foo").kind(EntityKind.CLASS).build(), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(c -> c.getOperation() != ChangeOperation.MODIFY));
    }

    @Test
    @DisplayName("matchCrossFileRefactors: METHOD 返回值不同 → 不匹配")
    void methodDifferentReturnType_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", methodEntity("com.old.Foo#m()", "void:int,String:a1b2"), 0));
        input.add(addition("New.java", methodEntity("com.new.Foo#m()", "String:int,String:a1b2"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: METHOD 参数类型不同 → 不匹配")
    void methodDifferentParams_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", methodEntity("com.old.Foo#m()", "void:int:String:a1b2"), 0));
        input.add(addition("New.java", methodEntity("com.new.Foo#m()", "void:long:String:a1b2"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: METHOD 签名相同但 body 不同 → 仍匹配 (0.7 ≥ 阈值)")
    void methodSameSignatureDifferentBody_Matched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", methodEntity("com.old.Foo#m()", "void:int:a1b2c3"), 0));
        input.add(addition("New.java", methodEntity("com.new.Foo#m()", "void:int:d4e5f6"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
        assertEquals(ChangeOperation.MODIFY, result.getFirst().getOperation());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: CLASS 继承不同 → 不匹配")
    void classDifferentSuper_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", classEntity("com.old.Foo", "HashMap::0:put(K,V)"), 0));
        input.add(addition("New.java", classEntity("com.new.Foo", "ArrayList::0:put(K,V)"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: CLASS 接口不同 → 不匹配")
    void classDifferentInterfaces_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", classEntity("com.old.Foo", ":Serializable:0:read()"), 0));
        input.add(addition("New.java", classEntity("com.new.Foo", ":Cloneable:0:read()"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: CLASS 字段数不同 → 相似度 0.3，低于阈值不匹配")
    void classDifferentFieldCount_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", classEntity("com.old.Foo", "::3:method()"), 0));
        input.add(addition("New.java", classEntity("com.new.Foo", "::1:method()"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: CLASS 方法集完全匹配 → Jaccard=1.0，匹配")
    void classFullMethodOverlap_Matched() {
        String sig = "::1:add(E);get(int);size()";
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", classEntity("com.old.Foo", sig), 0));
        input.add(addition("New.java", classEntity("com.new.Foo", sig), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
        assertEquals(ChangeOperation.MODIFY, result.getFirst().getOperation());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: CLASS 方法集部分重叠 → Jaccard=0.75≥阈值，匹配")
    void classPartialMethodOverlap_MatchedIfAboveThreshold() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", classEntity("com.old.Foo", "::1:add(E);get(int);size();clear()"), 0));
        input.add(addition("New.java", classEntity("com.new.Foo", "::1:add(E);get(int);size()"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: CLASS 无共同方法 → Jaccard=0，不匹配")
    void classNoMethodOverlap_NotMatched() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", classEntity("com.old.Foo", "::1:foo();bar()"), 0));
        input.add(addition("New.java", classEntity("com.new.Foo", "::1:baz();qux()"), 0));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 标志位合并——FEAT 保留并与 REFACTOR 合并")
    void flagsMerged() {
        String sig = "void::0";
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", methodEntity("com.old.Utils#run()", sig), ChangeNatureFlag.FEAT.code));
        input.add(addition("New.java", methodEntity("com.new.Utils#run()", sig), ChangeNatureFlag.FEAT.code));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
        int merged = ChangeNatureFlag.FEAT.code | ChangeNatureFlag.REFACTOR.code;
        assertEquals(merged, result.getFirst().getNatureFlagCode());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 无 REMOVE+ADD 时原样返回")
    void onlyModify_Unchanged() {
        List<ChangeLog> input = new ArrayList<>();
        input.add(modify("Foo.java", classEntity("com.foo.Foo", "::0:"), ChangeNatureFlag.FIX.code));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(1, result.size());
        assertEquals(ChangeOperation.MODIFY, result.getFirst().getOperation());
        assertEquals(ChangeNatureFlag.FIX.code, result.getFirst().getNatureFlagCode());
    }

    @Test
    @DisplayName("matchCrossFileRefactors: 一条 REMOVE 匹配一条 ADD，另一条 ADD 保留")
    void partialMatch_PreservesUnmatched() {
        String sig = "void::0";
        List<ChangeLog> input = new ArrayList<>();
        input.add(removal("Old.java", methodEntity("com.old.Foo#m()", sig), 0));
        input.add(addition("NewA.java", methodEntity("com.new.Foo#m()", sig), 0));
        input.add(addition("NewB.java", methodEntity("com.new.Bar#other()", "int::a1b2"), ChangeNatureFlag.FEAT.code));

        List<ChangeLog> result = engine.matchCrossFileRefactors(input);

        assertEquals(2, result.size());
        assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.MODIFY).count());
        assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        ChangeLog unmatched = result.stream()
                .filter(c -> c.getOperation() == ChangeOperation.ADD)
                .findFirst().orElseThrow();
        assertEquals("com.new.Bar#other()", unmatched.getEntity().getName());
    }
}
