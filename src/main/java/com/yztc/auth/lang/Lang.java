package com.yztc.auth.lang;

import net.minecraft.network.chat.Component;

public final class Lang {
    private static final String PREFIX = "yztauth.msg.";

    private Lang() {}

    public static String key(String id) {
        return PREFIX + id;
    }

    public static Component msg(String id, Object... args) {
        return Component.translatable(PREFIX + id, args);
    }
}
