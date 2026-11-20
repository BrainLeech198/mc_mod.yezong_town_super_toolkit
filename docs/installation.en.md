# Installation and upgrades

[Project overview](../README.en.md) · [中文部署与命令说明](../DEPLOY.md) · [Data and privacy](../PRIVACY.md)

## Choose the right package

Install the toolkit on the **server only**. Players use a vanilla Minecraft 1.20.1 client.

| Minecraft | Server platform | Runtime Java | Additional server mods |
|---|---|---|---|
| 1.20.1 | Forge 47.4.0 | 17 | No Fabric libraries |
| 1.20.1 | Fabric Loader 0.20.1 | 17 | Fabric API 0.92.12+1.20.1 and Forge Config API Port 8.0.3, Fabric edition |

Get stable files from [GitHub Releases](https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit/releases/latest). The filename identifies the platform: `yzt_super-<version>-mc1.20.1-forge.jar` or `yzt_super-<version>-mc1.20.1-fabric.jar`. Do not install both. PR workflow artifacts are candidates and are separate from stable releases. Version 1.4.0 remains the latest stable release; the 1.4.1 branch is still a candidate. The CurseForge project and its 1.4.0 files are under moderation review and are not publicly downloadable yet.

## New installation

1. Prepare the matching Minecraft server and Java 17. Stop the server before changing files.
2. Put one toolkit JAR in the server's `mods/` directory. For Fabric, install both required dependencies using their matching 1.20.1 Fabric files.
3. Start the server and check its console for missing dependencies or errors. No SAuth migration command is needed for a new server.
4. Join with a vanilla 1.20.1 client. A new account sets a password; an existing account uses its saved password. Follow the anvil instructions and click the right-hand submit item. Esc cancels the input.
5. After login, open `/yzts` to use the menu, or `/yzts help` for the in-game guide.

The mod does not provide automatic password recovery through email. A player who forgets a password should ask the server administrator to reset it.

## Configuration

World configuration is stored at `<world>/serverconfig/yzt_super-server.toml`; the world directory may have a custom name. Edit the existing values in the file rather than adding a duplicate TOML table. For example:

```toml
[feature]
language = "en_us"
restoreLastGameMode = true
auditLogEnabled = true
notifyAdminOnLogin = true
```

These are configuration examples, not a complete replacement file. `language` selects the server fallback messages. `restoreLastGameMode` restores the player's recorded logout mode after successful authentication. Audit logging and administrator login notifications include player names and IP addresses; see [privacy details](../PRIVACY.md). Restart and check the edited configuration before using it in production.

## Upgrade or switch loaders

1. Stop the server and take a consistent backup of the world, `config/`, world `serverconfig/` and `mods/`.
2. Remove the old toolkit JAR. For a loader change, prepare that loader and review every installed mod's compatibility before using the world.
3. Install the matching toolkit and declared dependencies. The toolkit keeps its data under `config/yzt_super/`; do not point two running servers at the same data.
4. Test login, failed-password retry, mail, permissions and game-mode restoration on a copy first. Check console errors and confirm persisted data after restarting.
5. If rollback is needed, stop the server and restore the matching loader, mods, configuration and data snapshot. Replacing a JAR alone does not reverse changes another mod made to a world.

## Existing login mods and SAuth migration

Other authentication mods may register `/login` and `/register`. Resolve command conflicts before using the toolkit. Migration from SAuth is optional and uses its existing `config/serverreg/users.json` as a read-only source.

Back up the source, remove the conflicting SAuth JAR and run these from the server console, without the in-game leading slash:

```text
yzts admin migrate --dry-run
yzts admin migrate
```

Inspect the dry-run results before committing the migration. Data is written to the toolkit's own store; imported legacy password hashes upgrade after a successful login. Keep the old backup for rollback. Full command and permission documentation is maintained in [DEPLOY.md](../DEPLOY.md).

## Troubleshooting

| Symptom | Check |
|---|---|
| Server fails to load the mod | Correct loader/JAR, Minecraft 1.20.1, Java 17 and Fabric dependencies |
| Messages use the wrong language | Existing `[feature]` / `language` value in the active world's TOML |
| Password forgotten | Administrator reset; email binding is not email delivery or password recovery |
| Account or mail cannot be saved | Server console, writable data directory and disk space; keep the original files and backups |
| Report needs logs | Share only relevant, redacted excerpts; never upload the account or mail databases |
