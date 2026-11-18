package com.yztc.auth.title;

import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.ui.ButtonPanel;
import com.yztc.auth.ui.Sounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 玩家选称号 GUI：列出 owned 称号（当前 selected 加光效），底部「不显示」。 */
public final class TitlePanel {
    private TitlePanel() {}

    /** 纯逻辑：面板槽位布局——标题占 0..n-1，「不显示」接其后；无标题时提示占 0、「不显示」占 1。 */
    public static List<Integer> slotsFor(int definedCount) {
        List<Integer> out = new ArrayList<>();
        if (definedCount <= 0) {
            out.add(1);
            return out;
        }
        for (int i = 0; i < definedCount; i++) out.add(i);
        out.add(definedCount);
        return out;
    }

    /** 纯逻辑：owned 里有定义的称号数（未定义 id 不占槽）。 */
    public static int definedCount(Set<String> owned, Map<String, TitleDef> defs) {
        if (owned == null || defs == null) return 0;
        int n = 0;
        for (String id : owned) if (defs.containsKey(id)) n++;
        return n;
    }

    public static void open(ServerPlayer viewer) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return;
        String name = TitleService.normalizeName(viewer.getGameProfile().getName());
        Set<String> owned = store.ownedOf(name);
        String selected = store.selectedOf(name);
        Map<String, TitleDef> defs = store.definitions();

        List<String> defined = new ArrayList<>();
        for (String id : owned) if (defs.containsKey(id)) defined.add(id);
        List<Integer> slots = slotsFor(defined.size());

        List<ButtonPanel.Btn> bs = new ArrayList<>();
        if (defined.isEmpty()) {
            bs.add(new ButtonPanel.Btn(Items.BARRIER, Lang.msg("title_no_owned"), null, 0));
        } else {
            for (int i = 0; i < defined.size(); i++) {
                String id = defined.get(i);
                TitleDef d = defs.get(id);
                boolean sel = id.equals(selected);
                Component label = Component.literal(d.text).withStyle(TitleService.colorOf(d.color));
                bs.add(new ButtonPanel.Btn(Items.NAME_TAG, label, v -> select(v, id), slots.get(i), sel));
            }
        }
        bs.add(new ButtonPanel.Btn(Items.BARRIER, Lang.msg("title_no_display"),
                TitlePanel::clear, slots.get(slots.size() - 1)));
        ButtonPanel.open(viewer, Lang.msg("title_panel_title"), bs);
    }

    private static void select(ServerPlayer v, String id) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return;
        String name = TitleService.normalizeName(v.getGameProfile().getName());
        if (!TitleService.canSelect(id, store.ownedOf(name), store.definitions())) return;
        String previous = store.selectedOf(name);
        store.select(name, id);
        try {
            store.save();
        } catch (IOException e) {
            if (previous.isBlank()) store.clearSelected(name);
            else store.select(name, previous);
            v.sendSystemMessage(Lang.msg("err_save", e.getMessage()));
            return;
        }
        TitleDef d = store.def(id);
        v.sendSystemMessage(Lang.msg("title_set_ok", d == null ? id : d.text));
        Sounds.ok(v);
        TitleEvents.refreshDisplay(v);
    }

    private static void clear(ServerPlayer v) {
        TitleStore store = StoreHolder.titles();
        if (store == null) return;
        String name = TitleService.normalizeName(v.getGameProfile().getName());
        if (!TitleService.hasSelected(store.selectedOf(name))) {
            v.sendSystemMessage(Lang.msg("title_none"));
            return;
        }
        String previous = store.selectedOf(name);
        store.clearSelected(name);
        try {
            store.save();
        } catch (IOException e) {
            store.select(name, previous);
            v.sendSystemMessage(Lang.msg("err_save", e.getMessage()));
            return;
        }
        v.sendSystemMessage(Lang.msg("title_clear_ok"));
        Sounds.ok(v);
        TitleEvents.refreshDisplay(v);
    }
}
