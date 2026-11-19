Server-side account login and player utilities for Minecraft 1.20.1, available as separate Forge and Fabric files. Players can connect with a vanilla client without installing this mod.

## Features

- Register an account, log in with a password and retry from the input screen.
- Optionally require a server access password before account login.
- Send private mail, read an inbox and use a public message board.
- Open `/yzts` for account settings, titles, last logout location, shouts and help.
- Manage accounts, announcements and risk-operation records with administrator permissions.
- Optionally restore the player's saved game mode after authentication.

## Installation

Use Minecraft **1.20.1** and Java **17**. Stop and back up the server before upgrading. Install exactly one matching toolkit JAR in the server's `mods/` directory and remove older toolkit JARs.

- **Forge:** the verified baseline is Forge **47.4.0**; the configuration system is included with Forge.
- **Fabric:** use Fabric Loader **0.20.1**, **Fabric API 0.92.12+1.20.1** and the **Fabric build of Forge Config API Port 8.0.3**. Both libraries are required.

Start the server, connect with a vanilla 1.20.1 client, follow the registration/login prompt and open `/yzts`. Configuration is in `<world>/serverconfig/yzt_super-server.toml`. Set `language = "en_us"` under `[feature]` for English server messages. Data is stored under `config/yzt_super/`; keep backups.

Password text is visible in the local input screen. Offline-mode connections are not encrypted. Existing login mods may conflict with `/login` or `/register`; follow the migration documentation before replacing them. Other Minecraft versions and NeoForge are not currently supported.

## Development disclosure

The source code was primarily generated using AI tools, including Codex. Documentation, translations, project text and the vector toolbox icon also involved AI. The icon is an illustration, not an in-game item or screenshot. No AI service is required to run the mod. Human maintainers direct requirements, review changes and maintain the project; verification details and limitations are documented separately.

## 中文说明

野粽乡超级工具箱为 Minecraft 1.20.1 服务端提供登录注册、可选全服通行密码、私信与留言板、称号、上次位置、喊话和管理员工具。玩家无需安装客户端 Mod，登录后通过 `/yzts` 打开菜单。

Forge 与 Fabric 分别下载对应文件，每次只安装一个。两端运行 Java 17；Fabric 另需上文所列的 Fabric API 与 Forge Config API Port。升级前停服并备份。主要源码、项目文字和矢量图标有 AI 生成，不代表游戏运行会调用 AI。

## Source, support and licence

- [Source and development provenance](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit)
- [Issue tracker](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/issues)
- [Deployment and migration documentation](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/blob/main/DEPLOY.md)

All Rights Reserved. Third-party dependencies retain their own licences. Public source access does not grant unrestricted redistribution rights.
