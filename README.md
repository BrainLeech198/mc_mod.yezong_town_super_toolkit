# 野粽乡超级工具箱

**Yezong Town Super Toolkit** 是面向 Minecraft 服务器的实用工具 Mod，当前适配 **Minecraft 1.20.1 / Forge 47**。它把登录注册、留言、玩家常用入口和管理员工具集中到一个服务端 Mod 中，并尽量用清楚的提示带玩家完成操作。

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-3C8527?logo=minecraft&logoColor=white)
![Forge 47](https://img.shields.io/badge/Forge-47-orange)
![Java 17](https://img.shields.io/badge/Java-17-blue?logo=openjdk&logoColor=white)
![Server side](https://img.shields.io/badge/Mod-server--side-informational)

新玩家进服后会看到注册或登录输入框：第一次设置自己的密码，以后使用同一密码登录。界面会说明在哪里输入、如何提交；输错后可以直接修改重试。玩家使用原版客户端即可，无须在客户端额外安装本 Mod。

## 功能一览

### 玩家功能

- **登录与注册**：自动判断账号状态，清楚提示首次设置密码或使用已有密码登录；管理员可重置忘记的密码。
- **全服闸门**：服主可选配额外的通行密码，在账号登录前验证。
- **留言**：玩家之间发送私信、查看收件箱与公开留言板。
- **个人工具**：账户设置、称号、上次下线位置、登出和全服喊话。
- **游戏模式恢复**：可配置在登录成功后恢复玩家上次退出时的游戏模式。
- **功能大厅**：通过 `/yzts` 打开统一菜单，减少记忆命令的需要。

### 管理员功能

- 查看账号、重置密码、删除账号、强制登录/登出和踢出玩家。
- 设置或关闭全服闸门，发布全服公告。
- 查询和标记高风险操作记录；支持从 SAuth 用户库迁移账号。
- 查看玩家上次下线位置，按权限传送到记录位置。

## 适用范围

| Minecraft | 服务端加载器 | 游戏 Java | 验证状态（2026-11-19） |
|---|---|---|---|
| 1.20.1 | Forge 47.4.0 | 17 | 构建通过、待运行验证（1.3.4 历史重映射打包记录） |
| 1.20.1 | Fabric Loader 0.20.1 + Fabric API 0.92.12+1.20.1 | 17 | 计划支持（首批提案，待确认） |
| 1.21.1 | NeoForge 21.1.267 | 21 | 计划支持（后续提案，待确认） |

目前“正在适配”和“已完成运行验证”均无条目。运行验证要求最终 JAR 在独立服务端启动，并由同版本原版客户端完成功能验收。现有 Forge 版本仅需在服务端安装，支持中英文提示和可配置的服务端兜底语言。[兼容性审计与候选组合](docs/兼容性审计.md)

本项目面向 Forge 服务端，不是 Bukkit / Paper 插件，也不适用于 Fabric。代理转发地址等部署差异有单独的适配说明，默认连接地址回退行为见 [代理客户端地址适配](docs/代理IP适配.md)。

## 安装

### 获取 Mod

正式发行包发布后，可从仓库的 [Releases](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/releases) 获取。若 Releases 页面尚无发行版，可按下面步骤从源码构建。

### 从源码构建

需要 JDK 17 和本机 Gradle 8.14.4。当前仓库未提交 Gradle Wrapper，因此需要自行安装 Gradle。构建前确认 `gradle --version` 显示的 JVM 为 17；本仓库的 `gradle.properties` 含有维护者本机的 JDK 路径，如该路径在你的环境不存在，请按[协作指南的 JDK 路径说明](docs/Codex协作指南.md#jdk-路径提示)在本机配置，不要提交个人绝对路径。

```bash
git clone https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit.git
cd mc_mod.yezong_town_super_toolkit
gradle build --no-daemon --console=plain
```

构建产物位于 `build/libs/yzt_super-<版本号>.jar`。把 JAR 放入 Forge 服务端的 `mods/` 目录并启动服务器。若原服务器安装了 SAuth，先备份数据并移除旧的 `SAuth-2.0.0.jar`，避免 `/login` 和 `/register` 指令冲突。迁移步骤见 [部署与迁移说明](DEPLOY.md)。

## 进入服务器后

1. 连接已安装本 Mod 的服务器。
2. 新账号按界面提示设置密码；已有账号输入原密码登录。
3. 登录后输入 `/yzts` 打开功能大厅。
4. 忘记密码时联系服务器管理员重置，不要把密码发到公共聊天或提交到 GitHub。

服主安装、配置、命令、权限、迁移和备份说明请见 **[DEPLOY.md](DEPLOY.md)**。玩家与服主可从 `/yzts help` 获取游戏内指南。

## 数据与安全

- 账号数据保存在服务端 `config/yzt_super/users.json`；新密码以 PBKDF2 哈希保存，不保存明文密码。迁移来的旧哈希会在首次成功登录后升级。
- 留言、位置与风险记录分别写入服务端配置目录下的 JSON 文件；升级或迁移前应备份 `config/`。
- 审计记录用于记录认证结果，不应包含密码。危险操作记录也不保存明文密码。
- 离线模式的 Minecraft 连接没有传输加密；这不是本 Mod 能解决的问题。公网部署请理解此限制，并采用符合服务器需求的网络与账号保护方案。

## 文档导航

- [当前交接状态与下一步](docs/交接说明.md)
- [提交与多人协作规范](CONTRIBUTING.md)
- [发布操作规范与模板](docs/发布规范.md)
- [部署、命令、配置与迁移](DEPLOY.md)
- [项目文档入口与维护规范](docs/README.md)
- [项目结构与主要调用链](docs/项目结构.md)
- [Codex 协作指南](docs/Codex协作指南.md)
- [主流 Mod 功能对照与路线图](docs/功能路线图.md)
- [代理客户端地址适配](docs/代理IP适配.md)
- [版本管理规范](docs/版本管理规范.md)
- [更新记录](CHANGELOG.md)
- [问题记录](docs/issues/)

## 参与协作

欢迎提交可复现的问题报告和改进建议。报告问题时请写明 Minecraft/Forge 版本、操作步骤、预期结果与实际结果；分享日志前请删除 IP、账号数据、令牌等隐私信息。

新协作者先读 [交接说明](docs/交接说明.md) 和 [提交与协作规范](CONTRIBUTING.md)，直接在原仓库工作分支协作，无需另建仓库。Codex 的环境准备见 [Codex 协作指南](docs/Codex协作指南.md)，仓库内 AI 工作规则见 [AGENTS.md](AGENTS.md)。Mod 版本按 [版本管理规范](docs/版本管理规范.md) 维护，发行按 [发布规范](docs/发布规范.md) 执行。

## 许可证

当前项目配置标记为 **All Rights Reserved**。公开仓库便于查看与协作，不代表源码可不受限制地重新发布或商用；使用与再分发请遵循仓库的权利声明，并联系维护者确认授权。
