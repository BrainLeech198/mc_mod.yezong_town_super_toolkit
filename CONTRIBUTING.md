# 提交与协作规范

本项目在原仓库内协作：`https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit`。已获得 Write 权限的协作者直接推送工作分支，不需要 Fork 或另外创建 GitHub 仓库。本地克隆只是工作副本。

## 开始任务

先读 [仓库规则](AGENTS.md)、[交接说明](docs/交接说明.md) 和 [Codex 协作指南](docs/Codex协作指南.md)。使用自己的 GitHub 登录凭据；不要向维护者索要账号密码或令牌。

在干净工作区执行以下流程；若已有改动，先保留并核对归属，再更新分支。

```bash
git remote -v
git status --short
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c codex/your-task
```

若本地没有 `main`，从 `origin/main` 建立跟踪分支。`--ff-only` 失败时先查看分歧，不能通过清空工作区或强推掩盖问题。

## 分工与并发

- 在 Issue 或 PR 中写清目标、负责范围和当前进度。提前建立 Draft PR 可让其他人知道正在修改哪些模块。
- 多人分别使用各自分支；调整公共构建、数据契约、认证状态或版本号前通知另一位维护者，避免各自实施不兼容方案。
- 提交前再次抓取远程状态。需要同步时，在自己的分支合入 `origin/main` 并解决冲突；保留两侧有效修复，再检查冲突涉及的调用链。
- 默认提交 PR，由维护者审查并合并。用户明确授权直接推送时可以执行；仓库是否有技术性分支保护需要单独核对，本规范不代表已配置保护规则。
- 不改写共享分支历史、不移动已发布标签、不替别人删除分支。

## 提交格式

使用 `类型(可选范围): 简短说明`，说明本次实际完成的事情。

| 类型 | 用途 | 示例 |
|---|---|---|
| `feat` | 新增能力 | `feat(mail): 增加未读筛选` |
| `fix` | 修复错误 | `fix(auth): 保存失败时拒绝注册放行` |
| `docs` | 说明与规范 | `docs: 更新平台适配交接状态` |
| `refactor` | 重构且不主动改变行为 | `refactor: 分离平台配置访问` |
| `build` | 构建与依赖 | `build: 增加平台构建模块` |
| `test` | 有意义的验证代码 | `test(mail): 覆盖混合大小写收件人` |
| `chore` | 维护事项 | `chore: 更新忽略规则` |

一个提交尽量对应一个可解释的目的；需要跨文件保持一致的契约改动一起提交。暂不公开的个人配置、运行数据与生成物不纳入提交。使用显式路径暂存，提交前检查 `git diff --cached`。

## 检查、推送与 PR

文档检查差异和链接；Java/资源/构建改动按影响执行编译、自测及必要的真实服务器验证。环境准备见 [Codex 协作指南](docs/Codex协作指南.md)，运行用例见 [部署说明](DEPLOY.md)。

```bash
git diff --check
git add <本次改动的文件路径>
git diff --cached --check
git diff --cached
git commit -m "docs: 更新平台适配交接状态"
git push -u origin HEAD
gh pr create --repo BrainLeech198/mc_mod.yezong_town_super_toolkit --base main --fill
```

尖括号内容须替换后再执行。没有 GitHub CLI 时，在原仓库页面用已推送分支创建 PR。PR 按仓库模板说明问题、行为变化、边界、兼容性、实际检查和未验证项。仅有构建结果时，不能填“真实服务器已验收”。

审核通过并合并后再进入 [发布流程](docs/发布规范.md)。普通文档提交不自动打版本标签、不创建 Mod 发行包。
