package com.yztc.auth.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

/** Fabric 的全局配置偏好不能改变既有 Forge 世界的安全设置。每个世界绑定自己的 SERVER 配置。 */
public final class FabricWorldConfig {
    private static final String FILE = "yzt_super-server.toml";
    private Path activeFile;
    private record Revision(FileTime modified, long size, Object fileKey) {}
    private Revision observed;
    private boolean unreadable;

    public static void register() {
        FabricWorldConfig config = new FabricWorldConfig();
        ServerLifecycleEvents.SERVER_STARTING.register(config::start);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) config.reloadIfChanged();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> config.close());
    }

    private void start(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE).normalize();
        Path template = FabricLoader.getInstance().getGameDir().resolve("defaultconfigs").resolve(FILE);
        try {
            Files.createDirectories(file.getParent());
            load(file, template);
            activeFile = file;
            observed = revision(file);
        } catch (IOException e) {
            throw new UncheckedIOException("无法加载工具箱世界配置 " + file, e);
        }
    }

    private static Revision revision(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        return new Revision(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey());
    }

    // Windows 编辑器可通过替换文件保存；只监听 MODIFY 会漏掉这种变更。
    private void reloadIfChanged() {
        if (activeFile == null) return;
        try {
            Revision next = revision(activeFile);
            if (next.equals(observed)) return;
            observed = next;
            load(activeFile, null);
            unreadable = false;
        } catch (IOException | RuntimeException e) {
            if (!unreadable) LoggerFactory.getLogger("yzt_super").error("世界配置重载失败，继续使用上次有效配置", e);
            unreadable = true;
        }
    }

    private static void load(Path file, Path defaultTemplate) throws IOException {
        boolean exists = !Files.notExists(file);
        Path source = exists ? file : defaultTemplate;
        if (!exists && defaultTemplate == null) throw new IOException("世界配置已不可读 " + file);
        CommentedConfig config = CommentedConfig.inMemory();
        if (source != null && !Files.notExists(source)) {
            try (var reader = Files.newBufferedReader(source)) {
                config = new TomlParser().parse(reader);
            }
        }
        boolean corrected = !AuthConfig.SPEC.isCorrect(config);
        if (corrected) AuthConfig.SPEC.correct(config);
        if (!exists || corrected) {
            Path tmp = Files.createTempFile(file.getParent(), FILE, ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(tmp)) {
                    new TomlWriter().write(config, writer);
                }
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        AuthConfig.SPEC.setConfig(config);
    }

    private void close() {
        activeFile = null;
        AuthConfig.SPEC.setConfig(null);
    }
}
