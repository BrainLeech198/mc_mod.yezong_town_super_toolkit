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

产物 `build/libs/yztauth-1.0.0.jar`（约 118 KB）。纯逻辑自测：

```bash
gradle selfTest --no-daemon --console=plain -Dfg.check.certs=false
# 期望末行：通过=276 失败=0
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

## 5.1 扩展指令（本批新增）

| 指令 | 说明 | 权限 |
|---|---|---|
| `/yztauth mail send <玩家> <内容>` | 发私信（收件人离线也可） | 所有人 |
| `/yztauth mail post <内容>` | 发公开留言板 | 所有人 |
| `/yztauth mail list` | 我的私信 + 公开板（列出即清未读） | 所有人 |
| `/yztauth mail board` | 只看公开板 | 所有人 |
| `/yztauth mail del <id>` | 删除留言 | 作者 / 收件人 / 管理员 |
| `/yztauth where <玩家>` | 查其最后下线维度与坐标 | 所有人 |
| `/yztauth tp <玩家>` | 传送到其记录位置 | 管理员、且须游戏内执行 |
| `/yztauth status` | 在线人数与未登录名单 | 管理员 / 控制台 |

未登录时这些指令一律被封锁拦截（与既有规则一致，只放行认证入口）。

## 6. 数据与配置落点

| 文件 | 位置 | 说明 |
|---|---|---|
| 账号库 | `config/yztauth/users.json` | PBKDF2 哈希，Base64 盐与摘要 |
| 留言库 | `config/yztauth/mail.json` | 私信与公开留言板（限量 + 超期自动清理） |
| 位置库 | `config/yztauth/lastseen.json` | 每个玩家最后下线位置（一人一条） |
| 审计日志 | `logs/yztauth-audit.log` | 登录/闸门结果行（名字 / IP / 结果词，无明文） |
| 服务器配置 | `world/serverconfig/yztauth-server.toml` | Forge SERVER 配置按世界存放 |
| SAuth 旧库 | `config/serverreg/users.json` | 只读迁移源，迁移完可归档 |

主要配置项（`world/serverconfig/yztauth-server.toml`）：`minPasswordLength`、`maxLoginAttempts`、`lockoutSeconds`、`pbkdf2Iterations`、`ipRateLimit.count`、`ipRateLimit.windowSeconds`、`loginTimeoutSeconds`、`ipResolution`、`forwardedHeaderName`、`logoutMode`、`logoutThrottleSeconds`、`legacyUsersFile`。

扩展功能配置（同文件 `[feature]` 段）：

| 键 | 默认 | 说明 |
|---|---|---|
| `mailMaxPerPlayer` | 20 | 每人私信上限，超出拒收 |
| `mailExpireDays` | 30 | 留言超期天数，超期自动清理 |
| `auditLogEnabled` | true | 是否写 `logs/yztauth-audit.log` |
| `notifyAdminOnLogin` | true | 玩家登录时是否通知在线管理员 |
| `afkLogoutEnabled` | false | AFK 自动登出开关（**默认关闭**） |
| `afkLogoutSeconds` | 600 | AFK 判定秒数（无位移即计时） |
| `welcomeTitleText` | 空 | 自定义欢迎标题（空=用语言键） |
| `welcomeSubText` | 空 | 自定义欢迎副标题（空=用语言键，`%s`=玩家名） |

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

自动化覆盖率：`gradle selfTest` 276 条纯逻辑用例全绿，覆盖哈希/存储/限流/迁移/语言/闸门/密码工具/UI 会话/书页 NBT/验密/闸门决策/登出禁重连/闸门限流，以及本批新增的密码强度/审计行格式/留言与位置存储/倒计时与欢迎语构建/Tab 隐藏纯逻辑/位置文本/状态文本/收件名校验/AFK 朝向判定/登出重隐藏。以下 32 条依赖真实客户端交互，**本次未由构建流程执行**，需在 `runServer` + `runClient` 手工走查后填写：

| # | 用例 | 结果 |
|---|---|---|
| 1 | 注册 → 登出 → 新密码登录；错密码被拒 | 待实测 |
| 2 | 密码边界：空 / 极短 / 极长 / Unicode / 含空格 | 待实测 |
| 3 | 重复注册被拒 | 待实测 |
| 4 | 连续错误触发锁定；锁定中正确密码仍拒；到期恢复 | 待实测 |
| 5 | 锁定状态跨服务器重启保持 | 待实测 |
| 6 | IP 限流：快速多次尝试被限 | 待实测 |
| 7 | 超时踢出：进服不登录，到时断开且消息正确 | 待实测 |
| 8 | 封锁完整性：未登录时移动回拉、右键/破坏/放置/攻击/丢弃/拾取、**执行任意指令**（只放行 `/login` `/register` `/yztauth gate\|login\|register\|help`）全拦；登录后恢复 | 待实测 |
| 9 | 迁移：混入非法 hex / 空键 / 重名条目，验证计数与逐条失败报告；`--dry-run` 不落库；重复幂等 | 待实测 |
| 10 | 迁移后老密码可登录，首次登录后该条变为 `pbkdf2-sha256` | 待实测 |
| 11 | 权限：装 LP 与不装 LP 下，普通 / OP / 管理员 / 最高权四档正确 | 待实测 |
| 12 | 危险操作二次验密：密码错时操作不执行 | 待实测 |
| 13 | 书 / 聊天按钮 / 铁砧 GUI 可点击与取消（含书内 clickEvent 确认） | 待实测 |
| 14 | 控制台执行 `/yztauth migrate` 成功 | 待实测 |
| 15 | 闸门：未设时直达登录；设后所有人先过闸，输错被限流、超时踢出；`clear` 后恢复直达 | 待实测 |
| 16 | 最高权分隔：`yztauth.admin` 不能改闸门密码；`yztauth.gate` 能改且需二次验密 | 待实测 |
| 17 | 控制台管理权限：重置密码 / 删号成功，无需二次验密 | 待实测 |
| 18 | 提示观感：标题持续闪烁；金标题 / 黄副标题 / 绿下划线可点按钮；聊天栏按钮文本与副标题不同；登录成功显示金色欢迎语 | 待实测 |
| 19 | 未装本 mod 的客户端被拒登入（`displayTest=MATCH_VERSION`）；装了同版本 mod 的客户端正常进服且提示为中文 | 待实测 |
| 20 | 倒计时：未登录提示副标题随闪烁显示「剩余 N 秒」，归零前被超时踢出 | 待实测 |
| 21 | 审计日志：输错密码后 `logs/yztauth-audit.log` 出现一行含名字/IP/`LOGIN_FAIL`，且**无明文** | 待实测 |
| 22 | Tab 隐藏：A 未登录时，B 的 Tab 看不到 A；A 登录后出现 | 待实测 |
| 23 | 首进广播：新玩家首次进服，全服收到欢迎广播；老玩家进服不广播 | 待实测 |
| 24 | 留言：A 给离线 B 发私信，B 登录收到未读提醒，`list` 可见；`post` 公开所有人可见；`del` 权限正确；私信超 20 条被拒 | 待实测 |
| 25 | 位置：A 登出后 `/yztauth where A` 显示坐标；管理员 `/yztauth tp A` 传送成功；普通玩家 tp 被拒；控制台 tp 给出提示 | 待实测 |
| 26 | 剩余次数：登录连输错密码，提示剩余次数递减；到 0 后提示锁定 | 待实测 |
| 27 | 密码强度：注册时用 `abc`(弱)、`abcdef12`(中)、`Abcdef1!x`(强) 分别注册，提示强度正确 | 待实测 |
| 28 | 登录通知管理员：管理员在线时，有玩家登录，管理员收到含 IP 提示；`notifyAdminOnLogin=false` 时不发 | 待实测 |
| 29 | `/yztauth status`：返回在线人数与未登录名单；普通玩家被拒 | 待实测 |
| 30 | 异地登录提醒：用不同 IP 再次登录，本人收到新 IP 提示；首次登录不提示 | 待实测 |
| 31 | AFK（默认关闭）：`afkLogoutEnabled=false` 时挂机不登出；置 true 且 `afkLogoutSeconds` 调小后，挂机超时自动登出 | 待实测 |
| 32 | 自定义欢迎语：`welcomeTitleText`/`welcomeSubText` 非空时，登录显示自定义文案且仍为金/黄配色 | 待实测 |

## 10. 已知限制

- 离线模式无传输加密，密码明文过网（指令与 GUI 皆然）；非本 mod 引入。
- 铁砧输入框内密码本地可见。
- 裸指令 `/login` `/register` 依赖 SAuth 已卸载。
