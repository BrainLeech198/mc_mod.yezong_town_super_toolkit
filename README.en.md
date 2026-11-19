# Yezong Town Super Toolkit

[简体中文](README.md) · [Stable downloads](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/releases/latest) · [Report an issue](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/issues)

Server-side account login, private mail, player utilities and administration for Minecraft 1.20.1. Forge and Fabric use separate packages. Players connect with a vanilla 1.20.1 client; no client-side installation is required.

<img src="assets/branding/icon.png" alt="Toolbox project icon" width="96" height="96">

## Features

- Account registration and password login, an optional server access password, retry prompts and account management.
- Private mail, inboxes and a public message board.
- A `/yzts` menu for account settings, titles, last logout location, shouts and help.
- Optional restoration of the player's last saved game mode after authentication.
- Administrator account tools, announcements, permissions and risk-operation records.

## Supported combinations

| Minecraft | Server platform | Runtime Java |
|---|---|---|
| 1.20.1 | Forge 47.4.0 | 17 |
| 1.20.1 | Fabric Loader 0.20.1, Fabric API 0.92.12+1.20.1, Forge Config API Port 8.0.3 | 17 |

NeoForge and other Minecraft versions are not currently delivered. The stable release is 1.4.0; this branch's 1.4.1 is a distribution-cleanup candidate, not a new public release or a published CurseForge project.

## Install

1. Stop the server and back up its world, configuration and existing mods.
2. Install **one** matching toolkit JAR in the server's `mods/` directory. Remove any older toolkit JAR. Fabric also requires both dependencies listed above; Forge uses its built-in configuration system.
3. Start the server and check its logs. Configuration is stored at `<world>/serverconfig/yzt_super-server.toml`.
4. Join using a vanilla 1.20.1 client. Follow the registration/login prompt, then open `/yzts`.

For English server messages, set `language = "en_us"` in the `[feature]` section of that TOML file. Data is stored under `config/yzt_super/`; back it up before updating. Existing SAuth installations must follow the [migration instructions](DEPLOY.md). New installations do not need a migration command.

Passwords are hashed with PBKDF2; legacy migrated hashes upgrade after a successful login. Password entry is visible in the local anvil screen. Offline-mode connections are not encrypted, so choose an appropriate network and account protection setup for your server. Do not upload real account data with bug reports.

## Build and contribute

Forge: JDK 17, run `./gradlew clean selfTest build` from the repository root.

Fabric: JDK 25 for the build, run `./gradlew clean build` from `platforms/fabric-1.20.1/`. Its output still targets Java 17. On Windows use `gradlew.bat` in the respective directory.

Start with [CONTRIBUTING.md](CONTRIBUTING.md). See [CHANGELOG.md](CHANGELOG.md), [deployment documentation](DEPLOY.md) and [validation evidence](docs/平台验收.md) for details and remaining test cases.

## Development provenance and licence

The source code was primarily generated with AI tools, including Codex. Documentation, translations and the geometric toolbox icon also involved AI. The icon is an illustration, not an in-game screenshot. No AI service is required at runtime. See [PROVENANCE.md](PROVENANCE.md).

The project retains [All Rights Reserved](LICENSE). Public source access is not an open-source licence. Dependencies retain their original licences; see [third-party notices](THIRD_PARTY_NOTICES.md).
