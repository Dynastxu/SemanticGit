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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SameParameterValue")
@DisplayName("AnalysisEngine 全量分析测试")
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

    static CommitMeta commitWithHash(String hash) {
        return CommitMeta.builder()
                .hash(hash)
                .message("commit " + hash)
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

    static ChangeLog removal(String filePath, Entity entity, int flags, CommitMeta commit) {
        return ChangeLog.builder()
                .commit(commit)
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

    static ChangeLog addition(String filePath, Entity entity, int flags, CommitMeta commit) {
        return ChangeLog.builder()
                .commit(commit)
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

    static Entity entityWithoutKind(String name, String signature) {
        return Entity.builder()
                .name(name)
                .language(EntityLanguage.JAVA)
                .signature(signature)
                .build();
    }

    // =========================================================================
    // 空输入与单侧操作
    // =========================================================================
    @Nested
    @DisplayName("空输入与单侧操作")
    class EmptyAndSingleSide {

        @Test
        @DisplayName("空列表输入 → 返回空列表")
        void emptyList_ReturnsEmpty() {
            List<ChangeLog> result = engine.matchCrossFileRefactors(Collections.emptyList());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("只有 REMOVE，没有 ADD → 全部原样保留")
        void onlyRemovals_AllPreserved() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("a.java", methodEntity("com.foo.A#m()", "void::0"), ChangeNatureFlag.FEAT.code));
            input.add(removal("b.java", methodEntity("com.foo.B#n()", "int::a1b2"), ChangeNatureFlag.FIX.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(c -> c.getOperation() == ChangeOperation.REMOVE));
            assertEquals(2, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
        }

        @Test
        @DisplayName("只有 ADD，没有 REMOVE → 全部原样保留")
        void onlyAdditions_AllPreserved() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(addition("a.java", methodEntity("com.foo.A#m()", "void::0"), ChangeNatureFlag.FEAT.code));
            input.add(addition("b.java", classEntity("com.foo.B", "::0:"), ChangeNatureFlag.FIX.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(c -> c.getOperation() == ChangeOperation.ADD));
        }

        @Test
        @DisplayName("只有 MODIFY → 原样返回")
        void onlyModify_Unchanged() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(modify("Foo.java", classEntity("com.foo.Foo", "::0:"), ChangeNatureFlag.FIX.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            assertEquals(ChangeOperation.MODIFY, result.getFirst().getOperation());
            assertEquals(ChangeNatureFlag.FIX.code, result.getFirst().getNatureFlagCode());
        }

        @Test
        @DisplayName("混合 MODIFY + ADD（无 REMOVE）→ 全部原样保留")
        void modifyWithAdd_AllPreserved() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(modify("Foo.java", classEntity("com.foo.Foo", "::0:"), ChangeNatureFlag.FIX.code));
            input.add(addition("Bar.java", methodEntity("com.foo.Bar#m()", "void::0"), ChangeNatureFlag.FEAT.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.MODIFY).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        }

        @Test
        @DisplayName("混合 MODIFY + REMOVE（无 ADD）→ 全部原样保留")
        void modifyWithRemove_AllPreserved() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(modify("Foo.java", classEntity("com.foo.Foo", "::0:"), ChangeNatureFlag.FIX.code));
            input.add(removal("Bar.java", methodEntity("com.foo.Bar#m()", "void::0"), ChangeNatureFlag.FEAT.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.MODIFY).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
        }
    }

    // =========================================================================
    // REMOVE+ADD 匹配成功
    // =========================================================================
    @Nested
    @DisplayName("REMOVE+ADD 匹配成功 → 转为 MODIFY")
    class SuccessfulMatching {

        @Test
        @DisplayName("相同签名的 CLASS 跨文件移动")
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
        @DisplayName("相同签名的 METHOD 跨文件移动")
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
        @DisplayName("同文件内相同签名 → 匹配为 REFACTOR")
        void sameFile_SameSignature_Matched() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Same.java", methodEntity("com.foo.Same#m()", sig), 0));
            input.add(addition("Same.java", methodEntity("com.foo.Same#m()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            ChangeLog matched = result.getFirst();
            assertEquals(ChangeOperation.MODIFY, matched.getOperation());
            assertEquals(ChangeNatureFlag.REFACTOR.code, matched.getNatureFlagCode());
        }

        @Test
        @DisplayName("同文件内相同签名但不同名称 → 重命名检测")
        void sameFile_Rename_Matched() {
            String sig = "int:int:a1b2c3";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Foo.java", methodEntity("com.foo.Foo#calculate(int)", sig), ChangeNatureFlag.FEAT.code));
            input.add(addition("Foo.java", methodEntity("com.foo.Foo#compute(int)", sig), ChangeNatureFlag.FEAT.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            ChangeLog matched = result.getFirst();
            assertEquals(ChangeOperation.MODIFY, matched.getOperation());
            assertEquals("com.foo.Foo#compute(int)", matched.getEntity().getName());
            assertEquals("com.foo.Foo#calculate(int)", matched.getParentEntity().getName());
            assertEquals(ChangeNatureFlag.FEAT.code | ChangeNatureFlag.REFACTOR.code, matched.getNatureFlagCode());
        }
    }

    // =========================================================================
    // REMOVE+ADD 匹配失败
    // =========================================================================
    @Nested
    @DisplayName("REMOVE+ADD 匹配失败 → 保持原操作不变")
    class FailedMatching {

        @Test
        @DisplayName("不同实体类型 → 不匹配")
        void differentKind_NotMatched() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Foo.java", classEntity("com.old.Foo", ": :0:method()"), 0));
            input.add(addition("Bar.java", methodEntity("com.new.Bar#method()", "void::0"), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
        }
    }

    // =========================================================================
    // 标志位合并
    // =========================================================================
    @Nested
    @DisplayName("标志位合并")
    class FlagMerging {

        @Test
        @DisplayName("REMOVE 与 ADD 同为 FEAT → FEAT | REFACTOR")
        void bothFeat_FeatAndRefactor() {
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
        @DisplayName("REMOVE=FIX, ADD=FEAT → 仅保留 ADD 的标志位 + REFACTOR（REMOVE 的 FIX 丢失）")
        void removeFixAddFeat_RemoveFlagLost() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Utils#run()", sig), ChangeNatureFlag.FIX.code));
            input.add(addition("New.java", methodEntity("com.new.Utils#run()", sig), ChangeNatureFlag.FEAT.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            int expected = ChangeNatureFlag.FEAT.code | ChangeNatureFlag.REFACTOR.code;
            assertEquals(expected, result.getFirst().getNatureFlagCode());
            assertFalse((result.getFirst().getNatureFlagCode() & ChangeNatureFlag.FIX.code) != 0,
                    "REMOVE 的 FIX 标志位未被保留");
        }

        @Test
        @DisplayName("REMOVE=FEAT, ADD=无标志位 → 仅 REFACTOR")
        void removeFeatAddZero_OnlyRefactor() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Utils#run()", sig), ChangeNatureFlag.FEAT.code));
            input.add(addition("New.java", methodEntity("com.new.Utils#run()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            assertEquals(ChangeNatureFlag.REFACTOR.code, result.getFirst().getNatureFlagCode());
        }

        @Test
        @DisplayName("REMOVE 与 ADD 均无标志位 → 仅 REFACTOR")
        void bothZero_OnlyRefactor() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Utils#run()", sig), 0));
            input.add(addition("New.java", methodEntity("com.new.Utils#run()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            assertEquals(ChangeNatureFlag.REFACTOR.code, result.getFirst().getNatureFlagCode());
        }
    }

    // =========================================================================
    // 多对匹配与竞争
    // =========================================================================
    @Nested
    @DisplayName("多对匹配与竞争")
    class MultipleMatching {

        @Test
        @DisplayName("一条 REMOVE 匹配一条 ADD，另一条 ADD 保留")
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

        @Test
        @DisplayName("两对 REMOVE+ADD 各自匹配 → 2 条 MODIFY")
        void twoPairs_MatchSeparately() {
            String sig1 = "void:int:a1b2c3";
            String sig2 = "int:String:d4e5f6";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m1(int)", sig1), ChangeNatureFlag.FEAT.code));
            input.add(removal("Old.java", methodEntity("com.old.Foo#m2(String)", sig2), ChangeNatureFlag.FIX.code));
            input.add(addition("New.java", methodEntity("com.new.Foo#m1(int)", sig1), ChangeNatureFlag.FEAT.code));
            input.add(addition("New.java", methodEntity("com.new.Foo#m2(String)", sig2), ChangeNatureFlag.FIX.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            List<ChangeLog> matched = result.stream()
                    .filter(c -> c.getOperation() == ChangeOperation.MODIFY)
                    .toList();
            assertEquals(2, matched.size());
        }

        @Test
        @DisplayName("两个 ADD 均可匹配同一 REMOVE → 仅第一个 ADD 被匹配（竞争匹配）")
        void twoAddsMatchSameRemove_FirstWins() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", sig), 0));
            input.add(addition("NewA.java", methodEntity("com.newA.Foo#m()", sig), ChangeNatureFlag.FEAT.code));
            input.add(addition("NewB.java", methodEntity("com.newB.Foo#m()", sig), ChangeNatureFlag.FIX.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.MODIFY).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        }

        @Test
        @DisplayName("一个 ADD 可匹配两个 REMOVE → 仅第一个 REMOVE 获得匹配")
        void oneAddMatchesTwoRemoves_FirstRemoveWins() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("OldA.java", methodEntity("com.oldA.Foo#m()", sig), 0));
            input.add(removal("OldB.java", methodEntity("com.oldB.Foo#m()", sig), ChangeNatureFlag.FEAT.code));
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", sig), ChangeNatureFlag.FIX.code));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.MODIFY).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
        }
    }

    // =========================================================================
    // NPE 防护
    // =========================================================================
    @Nested
    @DisplayName("空值防护")
    class NullSafety {

        @Test
        @DisplayName("REMOVE 的 entity 为 null → 跳过匹配，REMOVE 和 ADD 原样保留")
        void removedEntityNull_NotMatched() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(ChangeLog.builder()
                    .commit(dummyCommit())
                    .entity(null)
                    .operation(ChangeOperation.REMOVE)
                    .build());
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", "void::0"), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        }

        @Test
        @DisplayName("ADD 的 entity 为 null → 跳过匹配，REMOVE 和 ADD 原样保留")
        void addedEntityNull_NotMatched() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", "void::0"), 0));
            input.add(ChangeLog.builder()
                    .commit(dummyCommit())
                    .entity(null)
                    .operation(ChangeOperation.ADD)
                    .build());

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        }

        @Test
        @DisplayName("REMOVE 的 entity.kind 为 null → kind 比较不通过，不匹配，原样保留")
        void removedEntityKindNull_NotMatched() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", entityWithoutKind("com.old.Foo", "void::0"), 0));
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", "void::0"), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        }

        @Test
        @DisplayName("ADD 的 entity.kind 为 null → kind 比较不通过，不匹配，原样保留")
        void addedEntityKindNull_NotMatched() {
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", "void::0"), 0));
            input.add(addition("New.java", entityWithoutKind("com.new.Foo", "void::0"), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(2, result.size());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.REMOVE).count());
            assertEquals(1, result.stream().filter(c -> c.getOperation() == ChangeOperation.ADD).count());
        }
    }

    // =========================================================================
    // 跨 commit 与数据传递
    // =========================================================================
    @Nested
    @DisplayName("跨 commit 与数据传递")
    class CrossCommitConcerns {

        @Test
        @DisplayName("REMOVE 和 ADD 属于不同 commit → 匹配结果使用 ADD 的 commit")
        void crossCommit_MatchUsesAddedCommit() {
            String sig = "void::0";
            CommitMeta removeCommit = commitWithHash("111111");
            CommitMeta addCommit = commitWithHash("222222");

            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", sig), 0, removeCommit));
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", sig), 0, addCommit));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            ChangeLog matched = result.getFirst();
            assertEquals(ChangeOperation.MODIFY, matched.getOperation());
            assertEquals(addCommit.getHash(), matched.getCommit().getHash());
        }

        @Test
        @DisplayName("匹配后 dataQuality 和 analysisType 取自 ADD")
        void matchedResult_InheritsAddData() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", sig), 0));
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            ChangeLog matched = result.getFirst();
            assertEquals(DataQuality.AST, matched.getDataQuality());
            assertEquals(AnalysisType.INCREMENTAL, matched.getAnalysisType());
        }

        @Test
        @DisplayName("匹配结果 filePath 取自 ADD")
        void matchedResult_UsesAddedFilePath() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("old/path/Foo.java", methodEntity("com.old.Foo#m()", sig), 0));
            input.add(addition("new/path/Foo.java", methodEntity("com.new.Foo#m()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            assertEquals("new/path/Foo.java", result.getFirst().getFilePath());
        }

        @Test
        @DisplayName("匹配结果 entity 取自 ADD")
        void matchedResult_UsesAddedEntity() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", sig), 0));
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            assertEquals("com.new.Foo#m()", result.getFirst().getEntity().getName());
        }

        @Test
        @DisplayName("匹配结果 parentEntity 取自 REMOVE 的 entity")
        void matchedResult_ParentEntityFromRemoved() {
            String sig = "void::0";
            List<ChangeLog> input = new ArrayList<>();
            input.add(removal("Old.java", methodEntity("com.old.Foo#m()", sig), 0));
            input.add(addition("New.java", methodEntity("com.new.Foo#m()", sig), 0));

            List<ChangeLog> result = engine.matchCrossFileRefactors(input);

            assertEquals(1, result.size());
            assertEquals("com.old.Foo#m()", result.getFirst().getParentEntity().getName());
        }
    }
}
