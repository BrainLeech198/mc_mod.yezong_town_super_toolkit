# yztauth 部署与切换说明

用于把在用的 SAuth-2.0.0 换成本 mod。目标 mod id：`yztauth`，产物：`build/libs/yztauth-1.0.0.jar`。

## 1. 迁移前备份

按既有清单备份，重点三项：

- `world/`（含 `world/serverconfig/`）
- `config/`（尤其 `config/serverreg/users.json` —— SAuth 用户库，迁移数据源）
- `mods/`

`config/serverreg/users.json` 建议单独复制一份带日期的副本。迁移过程只读它，不写、不删。

## 2. 构建

```bash
cd /d/File/claude/mc_bluemap/yztauth
export PATH="$HOME/.gradle/wrapper/dists/gradle-8.14.4-bin/*/gradle-8.14.4/bin:$PATH"
gradle build --no-daemon --console=plain -Dfg.check.certs=false
```

产物 `build/libs/yztauth-1.0.0.jar`（约 73 KB）。纯逻辑自测：

```bash
gradle selfTest --no-daemon --console=plain -Dfg.check.certs=false
# 期望末行：通过=100 失败=0
```

## 3. 替换

1. 从 `mods/` 移除 `SAuth-2.0.0.jar`（务必移除：本 mod 的裸指令 `/login` `/register` 与 SAuth 冲突）。
2. 放入 `yztauth-1.0.0.jar`。
3. **保留** `config/serverreg/users.json` 原位不动（迁移要读它）。
4. 若在用其它登录类 mod，先确认不会与本 mod 抢同名裸指令。

## 4. 首次启动与迁移

启动服务器，然后**从控制台**执行：

```
/yztauth migrate --dry-run      # 先干跑：只报计数与逐条失败，不落库
/yztauth migrate                # 确认无误后落库
```

- 迁移结果只记计数与状态词（`IMPORTED` / `SKIPPED` / `FAILED`），不记明文密码。
- 重复执行幂等；`--dry-run` 不写库。
- 迁移写入 `config/yztauth/users.json`（新库，与 SAuth 文件无耦合）。
- 老密码为无盐 SHA-256，**首次成功登录后**该条自动升级为 `pbkdf2-sha256`。

## 5. 可选：全服闸门密码

只有最高权可设（控制台，或游戏内 `yztauth.gate` 权限者且需二次验密）。设置后**所有人（含管理员）**进服必须先过闸。

```
/yztauth serverpassword set <密码>    # 开启门禁
/yztauth serverpassword clear         # 关闭门禁，恢复进服直达登录
```

不设则跳过此步。

## 6. 数据与配置落点

| 文件 | 位置 | 说明 |
|---|---|---|
| 账号库 | `config/yztauth/users.json` | PBKDF2 哈希，Base64 盐与摘要 |
| 服务器配置 | `world/serverconfig/yztauth-server.toml` | Forge SERVER 配置按世界存放 |
| SAuth 旧库 | `config/serverreg/users.json` | 只读迁移源，迁移完可归档 |

主要配置项（`world/serverconfig/yztauth-server.toml`）：`minPasswordLength`、`maxLoginAttempts`、`lockoutSeconds`、`pbkdf2Iterations`、`ipRateLimit.count`、`ipRateLimit.windowSeconds`、`loginTimeoutSeconds`、`ipResolution`、`forwardedHeaderName`、`logoutMode`、`logoutThrottleSeconds`、`legacyUsersFile`。

`ipResolution=auto` 会尝试取代理头；直连环境保持 `auto` 即可，反代后置为 `header` 并核对 `forwardedHeaderName`。

## 7. 回滚

换回 `SAuth-2.0.0.jar` 即可。新库在 `config/yztauth/users.json`，与 `config/serverreg/users.json` 无耦合；回滚不影响旧文件。若回滚前有人改过密，需在 SAuth 侧另行重置。

## 8. 书页 clickEvent 支持性（已由字节码核实）

1.20.1 客户端支持书页内 clickEvent。核实路径（`forge-1.20.1-47.4.0_mapped_official_1.20.1.jar`）：

- `BookViewScreen.mouseClicked(double,double,int)` → `handleComponentClicked(getClickedComponentStyleAt(x,y))`
- `getClickedComponentStyleAt` 在 `cachedPageComponents` 上按 `font` 度量命中样式（含 clickEvent）
- `BookViewScreen.handleComponentClicked(Style)` → `Screen.handleComponentClicked`，后者分派 `OPEN_URL` / `OPEN_FILE` / `SUGGEST_COMMAND` / `RUN_COMMAND` / `COPY_TO_CLIPBOARD`；仅当动作为 `RUN_COMMAND` 时额外 `closeScreen()`

本 mod 的指南书用 `suggest_command`，故书页点击**预期生效**（把命令填入聊天框，由玩家确认发送）。此项已由客户端字节码核实，仍建议在下面走查用例 13 中做一次端到端确认。

## 9. 验收记录（Spec §7 的 17 条用例）

自动化覆盖率：`gradle selfTest` 129 条纯逻辑用例全绿，覆盖哈希/存储/限流/迁移/语言/闸门/密码工具/UI 会话/书页 NBT/验密/闸门决策/登出禁重连/闸门限流。以下 17 条依赖真实客户端交互，**本次未由构建流程执行**，需在 `runServer` + `runClient` 手工走查后填写：

| # | 用例 | 结果 |
|---|---|---|
| 1 | 注册 → 登出 → 新密码登录；错密码被拒 | 待实测 |
| 2 | 密码边界：空 / 极短 / 极长 / Unicode / 含空格 | 待实测 |
| 3 | 重复注册被拒 | 待实测 |
| 4 | 连续错误触发锁定；锁定中正确密码仍拒；到期恢复 | 待实测 |
| 5 | 锁定状态跨服务器重启保持 | 待实测 |
| 6 | IP 限流：快速多次尝试被限 | 待实测 |
| 7 | 超时踢出：进服不登录，到时断开且消息正确 | 待实测 |
| 8 | 封锁完整性：未登录时移动回拉、右键/破坏/放置/攻击/丢弃/拾取全拦；登录后恢复 | 待实测 |
| 9 | 迁移：混入非法 hex / 空键 / 重名条目，验证计数与逐条失败报告；`--dry-run` 不落库；重复幂等 | 待实测 |
| 10 | 迁移后老密码可登录，首次登录后该条变为 `pbkdf2-sha256` | 待实测 |
| 11 | 权限：装 LP 与不装 LP 下，普通 / OP / 管理员 / 最高权四档正确 | 待实测 |
| 12 | 危险操作二次验密：密码错时操作不执行 | 待实测 |
| 13 | 书 / 聊天按钮 / 铁砧 GUI 可点击与取消（含书内 clickEvent 确认） | 待实测 |
| 14 | 控制台执行 `/yztauth migrate` 成功 | 待实测 |
| 15 | 闸门：未设时直达登录；设后所有人先过闸，输错被限流、超时踢出；`clear` 后恢复直达 | 待实测 |
| 16 | 最高权分隔：`yztauth.admin` 不能改闸门密码；`yztauth.gate` 能改且需二次验密 | 待实测 |
| 17 | 控制台管理权限：重置密码 / 删号成功，无需二次验密 | 待实测 |

## 10. 已知限制

- 离线模式无传输加密，密码明文过网（指令与 GUI 皆然）；非本 mod 引入。
- 铁砧输入框内密码本地可见。
- 裸指令 `/login` `/register` 依赖 SAuth 已卸载。
