# SemanticGit

SemanticGit 是一个语义级 Git 仓库分析工具。不同于传统的文本 diff 分析，它通过解析源代码中的结构化实体（类、方法、函数等），从语义层面追踪和理解代码变更历史。

## 特性

- **语义级分析**：识别类、方法、函数等代码实体，追踪它们的增删改
- **多语言支持**：内置 Java、~~C++~~、~~Python~~ 解析器（目前仅实现 Java 解析）
- **全量历史分析**：遍历仓库全部提交，构建完整的实体变更时间线
- **SQLite 持久化**：分析结果存入本地数据库，支持离线查询和统计
- **命令行工具**：提供 `semgit analyze` 和 `semgit statistics` 子命令
- **国际化支持**：CLI 及 UI 支持中英文

## 模块结构

| 模块                | 说明                                                   |
|---------------------|--------------------------------------------------------|
| `common`            | 公共实体定义（Entity, ChangeLog, CommitMeta 等）和配置 |
| `parser:api`        | 语言解析器接口定义                                     |
| `parser:java`       | Java 源码解析器实现                                    |
| ~~`parser:cpp`~~    | ~~C++ 源码解析器实现~~（暂未实现）                     |
| ~~`parser:python`~~ | ~~Python 源码解析器实现~~（暂未实现）                  |
| `git`               | 基于 JGit 的 Git 操作封装                              |
| `core`              | 分析引擎、数据库管理、统计查询                         |
| `cli`               | 基于 picocli 的命令行界面                              |
| `ui`                | Compose Desktop 图形界面                               |

## 快速开始

### 环境要求

- JDK 25+
- Gradle 9.7+
- JavaFX SDK 25.0.4+

## 技术栈

- **语言**：Java 25
- **构建**：Gradle (Kotlin DSL)
- **Git 操作**：JGit
- **Java 解析**：JavaParser
- **数据库**：SQLite (via JDBI)
- **CLI 框架**：picocli
- **UI 框架**：Compose Desktop

## 许可

Apache License 2.0
