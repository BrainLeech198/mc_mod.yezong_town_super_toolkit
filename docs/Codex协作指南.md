# Codex 协作指南

这份指南帮助新协作者把本仓库交给 Codex 阅读、修改、检查和提交。项目是面向 Minecraft 1.20.1 / Forge 47 的 Java 17 服务端 Mod。Codex 可以协助代码与文档工作；实际服务器行为仍需要在目标环境验收。

## 开始协作

1. 接受仓库维护者发出的 GitHub 协作者邀请。
2. 克隆仓库并在 Codex 中打开仓库根目录：

   ```bash
   git clone https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit.git
   cd mc_mod.yezong_town_super_toolkit
   ```

   也可以在 Codex 的项目/文件夹选择器中直接打开克隆后的目录。不要把服务器运行目录、个人配置目录或整个 `.gradle` 缓存当作项目目录。

3. 确认当前仓库和工作区：

   ```bash
   git remote -v
   git status --short
   ```

   `origin` 应指向上面的仓库地址。开始修改前先处理已有未提交改动，避免覆盖其他工作。

## 本机开发环境

- JDK：Java 17。用 `java -version` 和 `gradle --version` 确认 Gradle 实际使用的是 Java 17。
- Gradle：本仓库目前没有提交 Gradle Wrapper（`gradlew` / `gradlew.bat`），需要在本机安装 Gradle。当前维护环境使用 Gradle 8.14.4；遇到版本兼容问题时先核对该版本，不要把个人 Gradle 安装路径写进仓库文档或提交记录。
- ForgeGradle：由 `build.gradle` 声明，当前插件版本为 `6.0.54`；依赖下载需要可访问配置的 Maven 仓库。
- 工作目录：Gradle 的 `run/`、`build/` 和 `.gradle/` 都是本机生成目录，不提交这些构建或运行产物。

### JDK 路径提示

当前 `gradle.properties` 中的 `org.gradle.java.home` 是维护者电脑上的 Windows JDK 路径。若该路径在你的电脑不存在，Gradle 可能无法启动。优先在用户级 Gradle 配置中设置自己的 JDK 路径（Windows 通常为 `%USERPROFILE%\.gradle\gradle.properties`，macOS/Linux 通常为 `~/.gradle/gradle.properties`），或在本地工作副本中临时调整该项；不要把个人绝对路径提交到项目。

配置后重新运行 `gradle --version`，确认 JVM 为 17。若需要提交 `gradle.properties` 的项目级改动，先确认它不包含个人目录、凭据或机器专属配置。

## 推荐协作流程

1. **描述目标和现象**：说明玩家看到什么、预期结果、操作步骤、Minecraft/Forge 版本及相关配置；不要附密码、访问令牌或真实账号库。
2. **让 Codex 先追调用链**：从命令、GUI、事件或配置入口找到状态变更和数据读写位置，再确定改动范围。认证相关可先看 [项目结构与运行链路](项目结构.md)。
3. **按小范围修改**：一次处理一个可复核的问题。行为、配置、命令、权限或数据格式发生变化时，同步更新对应说明。
4. **阅读差异**：用 `git diff --check` 和 `git diff` 检查空白错误、意外文件和生成物；确认敏感数据没有进入差异。
5. **执行适用的检查**：在仓库根目录运行：

   ```bash
   gradle selfTest --no-daemon --console=plain
   gradle build --no-daemon --console=plain
   ```

   `selfTest` 是项目自带的纯逻辑自测任务；`build` 会编译并打包。若环境不支持某项检查，记录具体原因和未覆盖部分，不要把未执行说成通过。
6. **使用分支提交**：建议从最新 `main` 创建 `codex/<简短主题>` 分支，提交清晰、单一目的的改动，并通过 GitHub Pull Request 合并。提交信息使用 Conventional Commits 风格，例如 `fix: 修复留言收件人匹配`、`docs: 增加 Codex 协作指南`。不要在多人协作时强推共享分支。
7. **汇报结果**：说明改了什么、关键文件、运行了哪些检查、检查结果、仍需服务器实际验证的事项，以及提交/PR 链接。

## 版本与文档维护

- Mod 版本唯一来源是 `gradle.properties` 的 `mod_version`。版本递增、变更记录、发行打包和标签规则见 [版本管理规范](版本管理规范.md)。普通文档更新不递增 Mod 版本。
- 发行时更新根目录 `CHANGELOG.md` 和需要同步的部署说明；不要手工修改构建生成的版本信息。
- 文档主入口是 [项目文档](README.md)，部署与命令主说明是仓库根目录 [`DEPLOY.md`](../DEPLOY.md)。文档位置和更新要求见 [文档编写规范](文档规范.md)。
- 缺陷应记录复现条件、预期/实际结果、影响、根因、修复范围和真实验证结果；还不能复现时标注待核实。
- 版本兼容、权限、安全边界和数据迁移有取舍时，把依据与风险讲清楚，不要仅以“构建成功”代替服务器行为验收。

## 验收边界

自动构建和纯逻辑自测不能代替 Minecraft 服务器/客户端交互。GUI、登录注册、权限插件兼容、原版客户端体验、Forge 事件时序等变化，需要在目标 Forge 47 服务器（必要时配合客户端）实际操作确认。没有目标服务器时，清楚标记为“待实测”，并给出维护者可以复现的步骤。

## 协作安全

- 不提交 `run/`、`prodserver/`、`logs/`、`build/`、缓存、服务器配置、真实账号/留言数据、密码、密钥或访问令牌。
- 用户日志与崩溃报告先检查并移除 IP、令牌、账号标识等敏感内容，再交给 Codex。
- 账号认证、权限与持久化改动要同时检查失败路径；危险操作不得记录明文密码。
- Codex 生成的改动由协作者检查后再提交；有疑问时先要求解释调用链和最小差异，不要直接合并无法理解的变更。
