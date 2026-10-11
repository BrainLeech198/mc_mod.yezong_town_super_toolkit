package com.yztc.auth.lang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yztc.auth.config.AuthConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务端语言兜底表：为未安装本 mod 的客户端提供可读文本。
 *
 * <p>装了本 mod 的客户端自带 {@code assets/yzt_super/lang/*.json}，走 {@code translatable} 的本地翻译；
 * 原版客户端没有这些语言键，{@code Component.translatableWithFallback} 会退回到此处提供的模板，
 * 模板里的参数由客户端套用。语言取 {@link AuthConfig#language()}（服务端兜底语言）。
 */
public final class ServerLang {
    private static final String PREFIX = "yzt_super.msg.";
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    private ServerLang() {}

    /** 配置语言下某 id 的原始模板（未代入参数）；缺失回退 zh_cn，再缺则返回 id 本身。 */
    public static String template(String id) {
        String lang = AuthConfig.language();
        String v = templateFor(lang, id);
        if (v == null && !"zh_cn".equalsIgnoreCase(lang)) v = templateFor("zh_cn", id);
        return v != null ? v : id;
    }

    /** 纯查询：指定语言下某 id 的模板；语言非法自动归一到 zh_cn，id 缺失返回 null。 */
    public static String templateFor(String lang, String id) {
        if (id == null || id.isBlank()) return null;
        return mapFor(lang).get(PREFIX + id);
    }

    private static Map<String, String> mapFor(String lang) {
        String key = "en_us".equalsIgnoreCase(lang) ? "en_us" : "zh_cn";
        return CACHE.computeIfAbsent(key, ServerLang::load);
    }

    private static Map<String, String> load(String lang) {
        String path = "/assets/yzt_super/lang/" + lang + ".json";
        try (InputStream in = ServerLang.class.getResourceAsStream(path)) {
            if (in == null) return Collections.emptyMap();
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            Map<String, String> m = new HashMap<>();
            for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            return m;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
