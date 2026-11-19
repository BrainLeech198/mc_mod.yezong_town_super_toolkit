# Data and privacy / 数据与隐私

This document describes this mod's current runtime storage and messages. It is not a privacy policy for every server running the mod. Server operators control their files, backups and player-data practices.

## Local server storage

| File | Stored information |
|---|---|
| `config/yzt_super/users.json` | Account names, password algorithm/salt/hash, optional bound email, last login IP/time, registration and lockout information |
| `config/yzt_super/mail.json` | Message sender, recipient, text, public/private flag, creation and read times |
| `config/yzt_super/lastseen.json` | Last logout position, dimension, orientation, time and game mode |
| `config/yzt_super/titles.json` | Title definitions, ownership and selected title |
| `config/yzt_super/risk.json` | Administrative actions, actors, targets and handling records |
| `logs/yzt_super-audit.log` | Authentication event time, player name, IP address and result when audit logging is enabled |

Password hashes are not plaintext passwords. Email addresses, IP addresses, mail and position records are readable server-side data; they are not end-to-end encrypted. Anvil password entry is visible on the local player's screen. Offline-mode Minecraft connections are not encrypted.

The mod has no implemented telemetry, automatic remote data upload or runtime generative-AI service in its own source code. AI use disclosed in [PROVENANCE.md](PROVENANCE.md) describes development. This statement covers this project's implementation, not unrelated server software or other mods.

## Visibility and retention

- The operator and anyone with filesystem access can read the stores and backups. Administrators can access account email and other information through the mod's management features.
- With `notifyAdminOnLogin` enabled, login notifications show the player's name and IP to online administrators. This option and `auditLogEnabled` are both enabled by default.
- Public board entries are intended to be public. Private mail is filtered by the mod's commands and menus, but remains readable to the server operator from storage. Last logout locations can be queried through the existing location command by authenticated players.
- Mail expiry is configurable, with a default of 30 days, and is applied on load/save and relevant cleanup paths. Risk records are limited to 500 entries. Account and title data do not have a general automatic expiry; audit logs are appended without automatic rotation by this mod.
- Account deletion does not automatically erase mail, positions, titles, risk records, audit logs or backups. Operators must handle a wider deletion request across those files and their backup policy.

## Sharing diagnostic information

Use synthetic accounts for reproduction. Before posting logs or screenshots, remove passwords, email addresses, IP addresses, tokens and unrelated player information. Do not upload the real `users.json` or `mail.json`. A rolling `.bak` file is also player data.

Relevant implementation: [Account](src/main/java/com/yztc/auth/data/Account.java), [AuditLog](src/main/java/com/yztc/auth/audit/AuditLog.java), [AuthManager](src/main/java/com/yztc/auth/auth/AuthManager.java), [MailStore](src/main/java/com/yztc/auth/data/MailStore.java) and [account deletion](src/main/java/com/yztc/auth/command/AdminCommands.java).

## 中文说明

数据保存在服务器本地，服主负责访问权限、保留时间、备份和玩家删除请求。账号库保存密码哈希，而邮箱、IP、留言和位置是服务端可读取的数据，并非端到端加密。绑定邮箱不会自动发送邮件，也不提供自动找回密码。

默认开启的登录通知会向在线管理员显示玩家名和 IP；默认开启的审计日志也会记录这些信息。公开留言板面向玩家展示；私信虽受命令和 GUI 的收件人检查约束，服主仍可从文件读取。已认证玩家可以通过现有位置查询命令查到下线位置。

留言默认 30 天过期，风险记录限制为 500 条；账号与称号没有统一自动过期机制，审计日志不由本 Mod 自动轮转。删号只删除账号记录，不会同时清除其他库、日志与备份。对外反馈只使用合成数据或脱敏片段，不上传真实账号库、留言库及其 `.bak`。

本项目运行源码未实现遥测、自动远程上传或生成式 AI 调用；AI 来源声明描述开发过程。
