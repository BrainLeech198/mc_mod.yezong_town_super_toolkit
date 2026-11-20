# Yezong Town Super Toolkit

**野粽乡超级工具箱**：Minecraft 1.20.1 的服务端账户与玩家工具，分别提供 Forge、Fabric 安装包。玩家使用原版客户端，无需安装本 Mod。

[English](README.en.md) · [下载稳定版](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/releases/latest) · [安装与配置](DEPLOY.md) · [问题反馈](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/issues)

<img src="assets/branding/icon.png" alt="工具箱示意图标" width="96" height="96">

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-3C8527?logo=minecraft&logoColor=white)
![Forge 47](https://img.shields.io/badge/Forge-47-orange)
![Fabric](https://img.shields.io/badge/Fabric-1.20.1-c7ba91)
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
| 1.20.1 | Forge 47.4.0 | 17 | 已完成运行验证（独立服、原版客户端及核心功能） |
| 1.20.1 | Fabric Loader 0.20.1 + Fabric API 0.92.12+1.20.1 + Forge Config API Port 8.0.3 | 17 | 已完成运行验证（独立服、原版客户端及核心功能） |

只把已交付的平台列在支持表中；NeoForge 和其他 Minecraft 版本尚未交付，候选方案见[兼容性审计](docs/兼容性审计.md)。当前稳定版为 1.4.0，本分支的 1.4.1 是公开发行整理候选，尚未正式发行。CurseForge 项目及 v1.4.0 Forge/Fabric 文件已提交审核，审核通过前不会公开下载。[发行说明与校验值](docs/releases/1.4.0.md) · [平台验收及待测场景](docs/平台验收.md)

## 安装

### 获取 Mod

发行包及 SHA-256 校验文件从仓库的 [Releases](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/releases) 获取。按服务端加载器选择 `yzt_super-1.4.0-mc1.20.1-forge.jar` 或 `yzt_super-1.4.0-mc1.20.1-fabric.jar`，每次只安装其中一个；Fabric 必需依赖见上表。开发者构建命令见[部署说明](DEPLOY.md#2-构建)。

### 快速安装

1. 停止服务器；已有世界先备份世界、配置与 `mods/`。
2. 将与服务端加载器匹配的一个工具箱 JAR 放进 `mods/`，移除旧工具箱包。Fabric 另装上表中的两个必需依赖。
3. 启动服务器，检查日志；世界配置位于 `<世界>/serverconfig/yzt_super-server.toml`。
4. 以原版 1.20.1 客户端连接。全新服务器无需执行 SAuth 迁移；已有登录 Mod 时先按[迁移说明](DEPLOY.md)处理命令与数据冲突。

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
- 邮箱、IP、留言及位置是服务器本地可读数据；默认的登录通知会向在线管理员显示玩家名和 IP。存储、可见性与保留方式见[数据与隐私说明](PRIVACY.md)。
- 离线模式的 Minecraft 连接没有传输加密；这不是本 Mod 能解决的问题。公网部署请理解此限制，并采用符合服务器需求的网络与账号保护方案。

## 文档导航

- [部署、命令、配置与迁移](DEPLOY.md)
- [English installation guide](docs/installation.en.md)
- [数据与隐私](PRIVACY.md)
- [代理客户端地址适配](docs/代理IP适配.md)
- [更新记录](CHANGELOG.md)
- [问题记录](docs/issues/)
- [开发与维护文档](docs/README.md)
- [CurseForge 投稿准备与平台评估](docs/平台发布.md)

## 参与协作

欢迎提交可复现的问题报告和改进建议。报告问题时请写明 Minecraft 和加载器版本、操作步骤、预期结果与实际结果；分享日志前请删除 IP、账号数据、令牌等隐私信息。

开发者从 [CONTRIBUTING.md](CONTRIBUTING.md) 开始；构建、交接和 AI 工作规则集中在[维护文档](docs/README.md)。

## 开发来源

本项目主要源码由 Codex 等 AI 工具生成，文档、翻译和工具箱矢量图标也有 AI 参与。运行 Mod 不需要 AI 服务。来源披露不替代测试与安全审查，详见 [PROVENANCE.md](PROVENANCE.md)。

## 许可证

当前采用 [All Rights Reserved](LICENSE)，没有改为开源许可证；重新分发或修改授权请联系维护者。第三方依赖保留各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
