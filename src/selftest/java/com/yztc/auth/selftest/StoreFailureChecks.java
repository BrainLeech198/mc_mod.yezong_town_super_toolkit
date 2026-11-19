package com.yztc.auth.selftest;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.MigrateService;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.Mail;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.Risk;
import com.yztc.auth.data.RiskStore;
import com.yztc.auth.security.PasswordHasher;
import com.yztc.auth.title.TitleStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class StoreFailureChecks {
    private StoreFailureChecks() {}

    static void run() {
        Assert.section("存储故障、重试及重启读回");
        try {
            Path dir = Files.createTempDirectory("yzt-store-failures");
            accounts(dir);
            lastSeen(dir);
            risk(dir);
            mail(dir);
            titles(dir);
            corruptFiles(dir);
        } catch (Exception e) {
            Assert.that(false, "故障检查异常 " + e);
        }
    }

    @FunctionalInterface
    private interface IoAction { void run() throws IOException; }

    private static void expectFailure(IoAction action, String label) throws IOException {
        try {
            action.run();
            Assert.that(false, label);
        } catch (IOException expected) {
            Assert.that(true, label);
        }
    }

    // A directory where the temporary file should be causes a real write failure on Windows and Unix.
    private static Path blockWrite(Path file) throws IOException {
        return Files.createDirectory(file.resolveSibling(file.getFileName() + ".tmp"));
    }

    private static void accounts(Path dir) throws IOException {
        Path file = dir.resolve("users.json");
        AccountStore store = new AccountStore(file);
        Account previous = new Account();
        previous.algo = PasswordHasher.ALGO_LEGACY;
        previous.hash = PasswordHasher.legacySha256Hex("test-only".toCharArray());
        previous.failedAttempts = AuthConfig.maxLoginAttempts() - 1;
        previous.lastIp = "192.0.2.1";
        store.putAndSave("alice", previous);
        GateManager gate = new GateManager(store);
        gate.set("fixture-gate");
        String gateHash = store.getGate().hash;
        String before = Files.readString(file);
        Path block = blockWrite(file);
        long now = System.currentTimeMillis();
        expectFailure(() -> AuthManager.verifyAndSaveLogin(store, "alice", "wrong", "192.0.2.2", now), "错密锁定写失败");
        Assert.eq(store.get("alice").failedAttempts, AuthConfig.maxLoginAttempts() - 1, "计数回滚");
        Assert.eq(store.get("alice").lockedUntil, 0, "锁定回滚");
        expectFailure(() -> AuthManager.verifyAndSaveLogin(store, "alice", "test-only", "192.0.2.2", now), "升级/登录写失败");
        Assert.that(store.get("alice").isLegacy(), "原哈希与旧算法保留");
        Assert.eq(store.get("alice").lastIp, "192.0.2.1", "上次地址保留");
        expectFailure(() -> store.putAndSave("bob", new Account()), "新增账号写失败");
        Assert.that(!store.contains("bob"), "失败注册无账号");
        expectFailure(gate::clear, "清除闸门写失败");
        expectFailure(() -> gate.set("next-gate"), "修改闸门写失败");
        Assert.eq(store.getGate().hash, gateHash, "保留原闸门");
        expectFailure(() -> MigrateService.migrateAndSave(Map.of("Alice", previous.hash, "Bob", previous.hash), store, now),
                "部分已有账号的迁移写失败");
        Assert.that(store.contains("alice") && !store.contains("bob"), "只撤回本批新增账号");
        Assert.eq(Files.readString(file), before, "故障期间原件未变");
        AccountStore restarted = new AccountStore(file);
        restarted.load();
        Assert.that(restarted.get("alice").isLegacy() && restarted.get("bob") == null, "重启后仍是原账号快照");
        Files.delete(block);
        Assert.eq(AuthManager.verifyAndSaveLogin(store, "alice", "wrong", "192.0.2.2", now),
                AuthManager.LoginResult.BAD, "恢复写入后触发锁定");
        restarted.load();
        long until = restarted.get("alice").lockedUntil;
        Assert.that(until > now, "锁定截止时间已落盘");
        Assert.eq(AuthManager.verifyAndSaveLogin(restarted, "alice", "test-only", "192.0.2.2", now),
                AuthManager.LoginResult.LOCKED, "重启仍锁定");
        Assert.eq(AuthManager.verifyAndSaveLogin(restarted, "alice", "test-only", "192.0.2.2", until + 1),
                AuthManager.LoginResult.OK, "到期后验证");
        store.load();
        Assert.that(!store.get("alice").isLegacy(), "登录后已持久化哈希升级");
        Assert.eq(store.get("alice").lastIp, "192.0.2.2", "登录后地址已持久化");
    }

    private static void lastSeen(Path dir) throws IOException {
        Path file = dir.resolve("lastseen.json");
        LastSeenStore store = new LastSeenStore(file);
        LastSeen old = new LastSeen();
        old.name = "Alice";
        old.gameMode = "survival";
        store.putAndSave(old);
        LastSeen update = new LastSeen();
        update.name = "Alice";
        update.gameMode = "creative";
        Path block = blockWrite(file);
        expectFailure(() -> store.putAndSave(update), "下线位置写失败");
        Assert.eq(store.get("alice").gameMode, "survival", "旧模式保留");
        update.name = "Bob";
        expectFailure(() -> store.putAndSave(update), "首次下线位置写失败");
        Assert.that(store.get("bob") == null, "首次失败不留下快照");
        Files.delete(block);
        store.putAndSave(update);
        store.load();
        Assert.eq(store.get("bob").gameMode, "creative", "重试成功后重启可读");
    }

    private static void risk(Path dir) throws IOException {
        Path file = dir.resolve("risk.json");
        RiskStore store = new RiskStore(file);
        for (int i = 0; i < RiskStore.MAX; i++) {
            Risk r = new Risk();
            r.id = "fixture-" + i;
            r.at = i;
            store.add(r);
        }
        store.save();
        Risk extra = new Risk();
        extra.id = "extra";
        extra.at = RiskStore.MAX;
        Path block = blockWrite(file);
        expectFailure(() -> store.addAndSave(extra), "满库追加写失败");
        Assert.that(store.byId("fixture-0") != null && store.byId("extra") == null, "容量淘汰随失败撤销");
        expectFailure(() -> store.markHandledAndSave("fixture-0", "tester", 1000), "处理状态写失败");
        Assert.eq(store.pendingCount(), RiskStore.MAX, "处理状态回滚");
        Files.delete(block);
        Assert.that(store.markHandledAndSave("fixture-0", "tester", 1000), "可重试处理");
        store.load();
        Assert.eq(store.byId("fixture-0").handledBy, "tester", "重启读回处理人");
    }

    private static void mail(Path dir) throws IOException {
        Path file = dir.resolve("mail.json");
        MailStore store = new MailStore(file);
        Files.writeString(file, """
                {"version":1,"mails":[
                 {"id":"a","from":"ALICE","to":"BOB","text":"fixture","createdAt":%d},
                 {"from":"alice","to":"bob","createdAt":"bad"}]}
                """.formatted(System.currentTimeMillis()));
        store.load();
        Assert.eq(store.inboxFor("bOb").size(), 1, "坏单条隔离、混合大小写收件箱");
        Mail expired = new Mail();
        expired.id = "expired";
        expired.from = "alice";
        expired.to = "bob";
        expired.text = "old";
        expired.createdAt = 0;
        store.add(expired);
        Path block = blockWrite(file);
        expectFailure(store::save, "过期清理写失败");
        expectFailure(() -> store.removeAndSave("a"), "删除写失败");
        Assert.eq(store.all().stream().map(m -> m.id).toList(), List.of("a", "expired"), "失败不清过期项或改变顺序");
        Files.delete(block);
        store.save();
        Assert.eq(store.size(), 1, "成功后才提交过期清理");
        store.load();
        Assert.eq(store.inboxFor("BOB").get(0).id, "a", "重启后收件箱一致");
    }

    private static void titles(Path dir) throws IOException {
        Path file = dir.resolve("titles.json");
        TitleStore store = new TitleStore(file);
        store.give("alice", "one");
        store.give("alice", "two");
        store.select("alice", "one");
        store.save();
        Path block = blockWrite(file);
        expectFailure(() -> store.setOwnershipAndSave("bob", "one", true), "首次授予写失败");
        Assert.that(!store.players().containsKey("bob"), "新称号记录随失败撤销");
        expectFailure(() -> store.setOwnershipAndSave("alice", "one", false), "收回选择中的称号写失败");
        Assert.eq(store.players().get("alice").owned, List.of("one", "two"), "已有称号顺序保留");
        Assert.eq(store.selectedOf("alice"), "one", "已选称号保留");
        Files.delete(block);
    }

    private static void corruptFiles(Path dir) throws IOException {
        Path bad = dir.resolve("broken.json");
        AccountStore users = new AccountStore(bad);
        LastSeenStore positions = new LastSeenStore(bad);
        MailStore mail = new MailStore(bad);
        RiskStore risk = new RiskStore(bad);
        TitleStore titles = new TitleStore(bad);
        checkCorrupt(bad, users::load, users::save);
        checkCorrupt(bad, positions::load, positions::save);
        checkCorrupt(bad, mail::load, mail::save);
        checkCorrupt(bad, risk::load, risk::save);
        checkCorrupt(bad, titles::load, titles::save);
    }

    private static void checkCorrupt(Path path, IoAction load, IoAction save) throws IOException {
        Files.writeString(path, "{invalid");
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        Files.writeString(backup, "{}");
        expectFailure(load, "损坏整库加载报告失败");
        expectFailure(save, "阻止用空库覆盖坏原件及备份");
        Assert.eq(Files.readString(path), "{invalid", "保全故障原件");
        Assert.eq(Files.readString(backup), "{}", "保全备份");
        Files.copy(backup, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        load.run();
        save.run();
    }
}
