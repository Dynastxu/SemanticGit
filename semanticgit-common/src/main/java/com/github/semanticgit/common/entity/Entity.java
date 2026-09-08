package com.github.semanticgit.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Entity {
    private Long id;
    /**
     * 全局唯一全限定名（跨语言规范）
     * <p>
     * Java: com.example.Service#process()<p>
     * Python: my_module.MyClass.my_method<p>
     * JS/TS: src/util.ts#formatDate
     */
    private String name;
    private EntityLanguage language;
    private EntityKind kind;
    /**
     * 简化名称（仅用于UI展示，不存数据库）
     * <p>
     * 例如：全限定名 "com.example.Service#process" 的简名是 "process"
     */
    private String simpleName;
    /**
     * 所属的父实体ID（仅用于内存中的层级构建，非持久化字段）
     * <p>
     * 例如：方法所属的类ID
     */
    private Long parentId;

    public static class EntityBuilder {
        public EntityBuilder parentId(@NonNull Entity entity) {
            this.parentId = entity.getId();
            return this;
        }
    }
}
