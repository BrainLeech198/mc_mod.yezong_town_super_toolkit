package com.yztc.auth.ui;

import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.versions.forge.ForgeVersion;

/**
 * 启动横幅所需的运行时元数据：本 mod 版本/作者、Minecraft 版本、Forge 版本。
 *
 * <p>全部取自运行时（mod 元数据、{@link SharedConstants}、{@link ForgeVersion}），
 * 而非硬编码；任一不可得时返回空串，不抛异常——横幅缺一列好过开服失败。
 * 本类只读运行时信息，纯展示逻辑在 {@link Banner}。
 */
public final class ModInfo {
    public static final String MOD_ID = "yzt_super";

    private ModInfo() {}

    /** 本 mod 显示名（PCL2 等从 mods.toml 的 displayName 读到的就是它）。 */
    public static String name() {
        IModInfo info = info();
        if (info == null) return "";
        String n = info.getDisplayName();
        return n == null ? "" : n;
    }

    public static String version() {
        IModInfo info = info();
        return info == null ? "" : info.getVersion().toString();
    }

    public static String author() {
        IModInfo info = info();
        if (info == null) return "";
        return info.getConfig() == null ? ""
                : info.getConfig().getConfigElement("authors")
                .map(Object::toString).orElse("");
    }

    public static String minecraft() {
        try {
            return SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            return "";
        }
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static String loaderLabel() {
        return com.yztc.auth.lang.ServerLang.template("banner_forge");
    }

    public static String loaderVersion() {
        return forge();
    }

    public static String forge() {
        try {
            return ForgeVersion.getVersion();
        } catch (Throwable t) {
            return "";
        }
    }

    private static IModInfo info() {
        try {
            if (ModList.get() == null) return null;
            return ModList.get().getModContainerById(MOD_ID)
                    .map(c -> c.getModInfo()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
