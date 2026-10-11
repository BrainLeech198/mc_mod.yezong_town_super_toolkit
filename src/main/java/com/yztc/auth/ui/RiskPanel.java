package com.yztc.auth.ui;

import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Risk;
import com.yztc.auth.data.RiskStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lang.ServerLang;
import com.yztc.auth.risk.RiskLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 高风险记录面板（GUI）：列出未处理记录，点某条展开详情页（时间/操作/操作者/目标/说明），
 * 详情页里再点「标记已处理」处理、点「返回列表」回到列表。
 */
public final class RiskPanel {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RiskPanel() {}

    /** 列表页：每条未处理记录一个可点开的纸物品。 */
    public static void open(ServerPlayer viewer) {
        RiskStore store = StoreHolder.risk();
        List<Risk> pending = store == null ? List.of() : store.pending();
        List<ButtonPanel.Btn> bs = new ArrayList<>();
        for (Risk r : pending) {
            Component label = Component.literal(RiskLog.line(r));
            String id = r.id;
            bs.add(new ButtonPanel.Btn(Items.PAPER, label, v -> openDetail(v, id)));
        }
        if (bs.isEmpty()) {
            bs.add(new ButtonPanel.Btn(Items.BARRIER, Lang.msg("risk_none"), null));
        }
        ButtonPanel.open(viewer, Lang.msg("risk_title"), bs);
    }

    /** 详情页：五行只读信息（槽 0..4）+ 「标记已处理」（槽 9）+「返回列表」（槽 11）。 */
    public static void openDetail(ServerPlayer viewer, String id) {
        RiskStore store = StoreHolder.risk();
        Risk r = store == null ? null : store.byId(id);
        if (r == null) {
            viewer.sendSystemMessage(Lang.msg("risk_done_none"));
            open(viewer);
            return;
        }
        List<Component> info = detailInfo(r, ZoneId.systemDefault());
        List<ButtonPanel.Btn> bs = new ArrayList<>();
        for (int i = 0; i < info.size(); i++) {
            bs.add(new ButtonPanel.Btn(Items.PAPER, info.get(i), null, i));
        }
        if (r.handled) {
            bs.add(new ButtonPanel.Btn(Items.GRAY_DYE, Lang.msg("risk_handled_tag"), null, 9));
        } else {
            bs.add(new ButtonPanel.Btn(Items.LIME_DYE, Lang.msg("btn_mark_handled"),
                    v -> markHandled(v, r.id), 9));
        }
        bs.add(new ButtonPanel.Btn(Items.ARROW, Lang.msg("btn_back"), RiskPanel::open, 11));
        ButtonPanel.open(viewer, Lang.msg("risk_detail_title", r.id), bs);
    }

    private static void markHandled(ServerPlayer v, String id) {
        RiskStore store = StoreHolder.risk();
        if (store != null) {
            store.markHandled(id, v.getGameProfile().getName().toLowerCase(Locale.ROOT),
                    System.currentTimeMillis());
            try {
                store.save();
            } catch (IOException e) {
                v.sendSystemMessage(Lang.msg("err_save", e.getMessage()));
            }
        }
        v.sendSystemMessage(Lang.msg("risk_done_ok"));
        open(v);
    }

    /** 纯逻辑：时间戳 → "yyyy-MM-dd HH:mm:ss"（给定时区）。 */
    public static String timeText(long at, ZoneId zone) {
        return Instant.ofEpochMilli(at).atZone(zone).format(TS);
    }

    /** 纯逻辑：一条记录 → 五行详情（时间/操作/操作者/目标/说明；空值回退「(无)」）。 */
    public static List<Component> detailInfo(Risk r, ZoneId zone) {
        List<Component> out = new ArrayList<>();
        out.add(Lang.msg("risk_row_time", timeText(r.at, zone)));
        out.add(Lang.msg("risk_row_action", RiskLog.display(r.action)));
        out.add(Lang.msg("risk_row_actor", orNone(r.actor)));
        out.add(Lang.msg("risk_row_target", orNone(r.target)));
        out.add(Lang.msg("risk_row_detail", orNone(r.detail)));
        return out;
    }

    private static String orNone(String s) {
        return (s == null || s.isBlank()) ? ServerLang.template("risk_detail_none") : s;
    }
}
